package com.odiparpack.api.controller;

import com.google.gson.JsonObject;
import com.odiparpack.SimulationRunner;
import com.odiparpack.api.controller.routers.BaseRouter;
import com.odiparpack.api.controller.routers.LocationRouter;
import com.odiparpack.api.controller.routers.SimulationRouter;
import com.odiparpack.api.controller.routers.VehicleRouter;
import com.odiparpack.models.SimulationState;
import com.odiparpack.websocket.VehicleWebSocketHandler;
import spark.Spark;

import static spark.Spark.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class SimulationController {
    private final SimulationState simulationState;
    private final List<BaseRouter> routers;
    private ExecutorService simulationExecutor;
    private Future<?> simulationFuture;
    private static final Logger logger = Logger.getLogger(SimulationController.class.getName());

    public SimulationController(SimulationState simulationState) {
        this.simulationState = simulationState;
        this.routers = Arrays.asList(
                new LocationRouter(),
                new VehicleRouter(simulationState),
                new SimulationRouter(simulationState)
        );
    }

    public void start() {
        try {
            port(4567);
            setupWebSocket();
            configureServer();
            initializeRoutes();
            logger.info("Servidor de simulación iniciado en http://localhost:4567");
        } catch (Exception e) {
            logger.severe("Error al iniciar el servidor: " + e.getMessage());
            throw new RuntimeException("Error al iniciar el servidor", e);
        }
    }

    private void configureServer() {
        setupCORS();
        setupExceptionHandling();
    }

    private void setupWebSocket() {
        webSocket("/ws", VehicleWebSocketHandler.class);
        VehicleWebSocketHandler.setSimulationState(simulationState);
    }

    private void initializeRoutes() {
        // Ruta base
        get("/", (request, response) -> {
            response.type("text/html");
            return "<h1>Servidor de Simulación de Vehículos</h1>" +
                    "<p>API REST disponible en /api/v1/</p>";
        });

        // Inicializar todas las rutas de los routers
        routers.forEach(BaseRouter::setupRoutes);
    }

    private void setupCORS() {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            response.type("application/json");
        });
    }

    private void setupExceptionHandling() {
        exception(Exception.class, (exception, request, response) -> {
            logger.severe("Error no manejado: " + exception.getMessage());
            response.status(500);
            response.type("application/json");
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Error interno del servidor");
            errorResponse.addProperty("message", exception.getMessage());
            response.body(errorResponse.toString());
        });

        // Manejo de rutas no encontradas
        notFound((request, response) -> {
            response.type("application/json");
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Ruta no encontrada");
            errorResponse.addProperty("path", request.pathInfo());
            return errorResponse;
        });
    }

    public void stop() {
        try {
            if (simulationExecutor != null) {
                simulationExecutor.shutdownNow();
                simulationExecutor = null;
            }
            Spark.stop();
            logger.info("Servidor detenido correctamente");
        } catch (Exception e) {
            logger.severe("Error al detener el servidor: " + e.getMessage());
        }
    }

    // Métodos auxiliares para el manejo de la simulación
    private void startSimulationThread() {
        if (simulationExecutor != null && !simulationExecutor.isShutdown()) {
            logger.warning("Intento de iniciar una simulación cuando ya hay una en ejecución");
            return;
        }

        simulationExecutor = Executors.newSingleThreadExecutor();
        simulationFuture = simulationExecutor.submit(() -> {
            try {
                SimulationRunner.runSimulation(simulationState);
            } catch (Exception e) {
                logger.severe("Error en el hilo de simulación: " + e.getMessage());
            }
        });
    }
}