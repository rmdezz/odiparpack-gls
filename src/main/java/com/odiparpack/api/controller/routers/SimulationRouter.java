package com.odiparpack.api.controller.routers;

import com.google.gson.JsonObject;
import com.odiparpack.models.SimulationState;
import com.odiparpack.SimulationRunner;
import spark.Spark;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class SimulationRouter extends BaseRouter {
    private final SimulationState simulationState;
    private static final Logger logger = Logger.getLogger(SimulationRouter.class.getName());

    private ExecutorService simulationExecutor;
    private Future<?> simulationFuture;
    private volatile boolean isSimulationRunning = false;
    private volatile boolean isShutdown = false;

    public SimulationRouter(SimulationState simulationState) {
        this.simulationState = simulationState;
    }

    @Override
    public void setupRoutes() {
        // Iniciar simulación
        Spark.post("/api/v1/simulation/start", (request, response) -> {
            response.type("application/json");

            if (isSimulationRunning) {
                response.status(400);
                return createErrorResponse("La simulación ya está en ejecución.");
            }

            // Reiniciar el estado si estaba apagado
            if (isShutdown) {
                resetSimulationState();
            }

            startSimulation();
            response.status(200);
            return createSuccessResponse("Simulación iniciada.");
        });

        // Pausar simulación
        Spark.post("/api/v1/simulation/pause", (request, response) -> {
            response.type("application/json");

            if (!isSimulationRunning) {
                response.status(400);
                return createErrorResponse("La simulación no está en ejecución.");
            }

            pauseSimulation();
            response.status(200);
            return createSuccessResponse("Simulación pausada.");
        });

        // Detener simulación
        Spark.post("/api/v1/simulation/stop", (request, response) -> {
            response.type("application/json");

            if (!isSimulationRunning) {
                response.status(400);
                return createErrorResponse("La simulación no está en ejecución.");
            }

            stopSimulation();
            response.status(200);
            return createSuccessResponse("Simulación detenida.");
        });

        // Obtener estado actual de la simulación
        Spark.get("/api/v1/simulation/status", (request, response) -> {
            response.type("application/json");
            JsonObject status = new JsonObject();
            status.addProperty("currentTime", simulationState.getCurrentTime().toString());
            status.addProperty("isRunning", isSimulationRunning);
            status.addProperty("isShutdown", isShutdown);
            return status;
        });

        // Reiniciar simulación
        Spark.post("/api/v1/simulation/reset", (request, response) -> {
            response.type("application/json");

            try {
                resetSimulationState();
                return createSuccessResponse("Simulación reiniciada exitosamente.");
            } catch (Exception e) {
                logger.severe("Error al reiniciar la simulación: " + e.getMessage());
                response.status(500);
                return createErrorResponse("Error al reiniciar la simulación: " + e.getMessage());
            }
        });
    }

    private void resetSimulationState() {
        isShutdown = false;
        isSimulationRunning = false;
        if (simulationExecutor != null && !simulationExecutor.isShutdown()) {
            simulationExecutor.shutdownNow();
        }
        simulationExecutor = null;
        simulationFuture = null;
        simulationState.reset();
    }

    private void startSimulation() {
        isSimulationRunning = true;
        simulationExecutor = Executors.newSingleThreadExecutor();
        simulationFuture = simulationExecutor.submit(() -> {
            try {
                SimulationRunner.runSimulation(simulationState);
            } catch (Exception e) {
                logger.severe("Error en la simulación: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isSimulationRunning = false;
            }
        });
    }

    private void stopSimulation() {
        simulationState.stopSimulation();
        isSimulationRunning = false;
        isShutdown = true;
        if (simulationExecutor != null) {
            simulationExecutor.shutdownNow();
            simulationExecutor = null;
        }
        simulationFuture = null;
    }

    private void pauseSimulation() {
        simulationState.pauseSimulation();
    }

    private JsonObject createSuccessResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", message);
        return response;
    }

    private JsonObject createErrorResponse(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("error", message);
        return response;
    }
}