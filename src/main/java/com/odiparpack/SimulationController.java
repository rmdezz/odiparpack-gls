/*
package com.odiparpack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.odiparpack.models.Position;
import com.odiparpack.models.Vehicle;
import com.odiparpack.simulation.SimulationEngine;
import com.odiparpack.simulation.state.SimulationState;
import com.odiparpack.websocket.VehicleWebSocketHandler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class SimulationController {
    private final SimulationState simulationState;
    private final SimulationEngine simulationEngine;
    private final Gson gson = new Gson();

    // Constructor
    public SimulationController(SimulationState simulationState, SimulationEngine simulationEngine) {
        this.simulationState = simulationState;
        this.simulationEngine = simulationEngine;
    }

    // Método para iniciar el servidor
    public void start() {
        port(4567);

        // Configurar WebSockets
        configureWebSockets();
        VehicleWebSocketHandler.setSimulationState(simulationState);

        setupRoutes();

        System.out.println("Servidor de simulación iniciado en http://localhost:4567");
    }

    private void setupRoutes() {
        // Middleware para manejar CORS
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
            response.header("Access-Control-Allow-Origin", "*"); // Permitir todas las solicitudes
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        });

        // Endpoint de bienvenida
        get("/", (request, response) -> {
            response.type("text/html");
            return "<h1>Bienvenido al Servidor de Simulación de Vehículos</h1>" +
                    "<p>Para interactuar con la simulación, utiliza los endpoints adecuados.</p>";
        });

        // Endpoint para provocar una avería
        post("/breakdown", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");
            String breakdownType = request.queryParams("breakdownType");

            if (vehicleCode == null || breakdownType == null) {
                response.status(400);
                return "Parámetros faltantes: vehicleCode y breakdownType son obligatorios.";
            }

            // Validar el tipo de avería
            if (!isValidBreakdownType(breakdownType)) {
                response.status(400);
                return "Tipo de avería inválido. Debe ser 1, 2 o 3.";
            }

            // Provocar la avería a través de VehicleManager
            simulationState.getVehicleManager().provocarAveria(vehicleCode, breakdownType, simulationState.getCurrentTime());

            response.status(200);
            return "Avería procesada para el vehículo " + vehicleCode;
        });

        // Endpoint para obtener mensajes de averías
        get("/breakdown/messages", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            List<String> messages = simulationState.getVehicleManager().getBreakdownLogs(vehicleCode);

            if (messages == null || messages.isEmpty()) {
                response.status(404);
                return "No se encontraron mensajes de avería para el vehículo " + vehicleCode;
            }

            response.type("application/json");
            return gson.toJson(messages);
        });

        // Endpoint para obtener la posición de un vehículo
        get("/vehicle/position", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            Vehicle vehicle = simulationState.getVehicleManager().getVehicles().get(vehicleCode);
            if (vehicle == null) {
                response.status(404);
                return "No se encontró el vehículo con código " + vehicleCode;
            }

            Position position = vehicle.getCurrentPosition(simulationState.getCurrentTime());

            if (position == null) {
                response.status(500);
                return "No se pudo calcular la posición del vehículo " + vehicleCode;
            }

            // Crear Feature GeoJSON
            Map<String, Object> geoJsonFeature = new HashMap<>();
            geoJsonFeature.put("type", "Feature");

            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", Arrays.asList(position.getLongitude(), position.getLatitude()));
            geoJsonFeature.put("geometry", geometry);

            Map<String, Object> properties = new HashMap<>();
            properties.put("vehicleCode", vehicleCode);
            geoJsonFeature.put("properties", properties);

            response.type("application/json");
            return gson.toJson(geoJsonFeature);
        });

        // Endpoint para obtener las posiciones de todos los vehículos
        get("/vehicles/positions", (request, response) -> {
            // Utilizar el método proporcionado en VehicleManager
            JsonObject positionsGeoJSON = simulationState.getVehicleManager().getCurrentPositionsGeoJSON(simulationState.getCurrentTime());

            response.type("application/json");
            return gson.toJson(positionsGeoJSON);
        });

        // Endpoint para iniciar la simulación
        post("/simulation/start", (request, response) -> {
            if (simulationEngine.isRunning()) {
                response.status(400);
                return "La simulación ya está en ejecución.";
            }

            simulationEngine.startSimulation();
            response.status(200);
            return "Simulación iniciada.";
        });

        // Endpoint para pausar la simulación
        post("/simulation/pause", (request, response) -> {
            if (!simulationEngine.isRunning()) {
                response.status(400);
                return "La simulación no está en ejecución.";
            }
            simulationEngine.pauseSimulation();
            response.status(200);
            return "Simulación pausada.";
        });

        // Endpoint para detener la simulación
        post("/simulation/stop", (request, response) -> {
            if (!simulationEngine.isRunning()) {
                response.status(400);
                return "La simulación no está en ejecución.";
            }
            simulationEngine.stopSimulation();
            response.status(200);
            return "Simulación detenida.";
        });

        // Endpoint para obtener el historial de posiciones de un vehículo
        get("/vehicle/positions/history", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            Vehicle vehicle = simulationState.getVehicleManager().getVehicles().get(vehicleCode);
            if (vehicle == null) {
                response.status(404);
                return "No se encontró el vehículo con código " + vehicleCode;
            }

            List<Vehicle.PositionTimestamp> history = vehicle.getPositionHistory();

            if (history == null || history.isEmpty()) {
                response.status(404);
                return "No se encontró historial de posiciones para el vehículo " + vehicleCode;
            }

            // Convertir el historial a una lista de objetos JSON
            List<Map<String, Object>> historyJson = history.stream()
                    .map(pt -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("timestamp", pt.getTimestamp().toString());
                        map.put("latitude", pt.getPosition().getLatitude());
                        map.put("longitude", pt.getPosition().getLongitude());
                        return map;
                    })
                    .collect(Collectors.toList());

            response.type("application/json");
            return gson.toJson(historyJson);
        });

        // Manejo global de excepciones
        exception(Exception.class, (exception, request, response) -> {
            exception.printStackTrace();
            response.status(500);
            response.body("Error interno del servidor: " + exception.getMessage());
        });

        after((request, response) -> {
            System.out.println("Respuesta enviada para " + request.pathInfo() + " con estado " + response.status());
        });

    }

    // Método para configurar WebSockets
    private void configureWebSockets() {
        webSocket("/ws", VehicleWebSocketHandler.class);
    }

    // Método para validar el tipo de avería
    private boolean isValidBreakdownType(String breakdownType) {
        return breakdownType.equals("1") || breakdownType.equals("2") || breakdownType.equals("3");
    }
}
*/
package com.odiparpack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.odiparpack.models.Location;
import com.odiparpack.models.Position;
import com.odiparpack.models.SimulationState;
import com.odiparpack.models.Vehicle;
//import com.odiparpack.websocket.VehicleWebSocketHandler;
import com.odiparpack.services.LocationService;
import com.odiparpack.websocket.VehicleWebSocketHandler;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import spark.Service;

import static spark.Spark.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SimulationController {
    private final SimulationState simulationState;
    private final Gson gson = new Gson();
    private ExecutorService simulationExecutor;
    private Future<?> simulationFuture;
    private volatile boolean isSimulationRunning = false;
    private volatile boolean isShutdown = false;

    // Constructor
    public SimulationController(SimulationState simulationState) {
        this.simulationState = simulationState;
    }

    // Método para iniciar el servidor
    public void start() {
        port(4567);

        // Configurar WebSockets
        configureWebSockets();
        VehicleWebSocketHandler.setSimulationState(simulationState);

        setupRoutes();

        System.out.println("Servidor de simulación iniciado en http://localhost:4567");
    }

    // Método para iniciar el servidor
    /*public void start() {
        port(4567);

        // Configure WebSockets before any route mapping
        //configureWebSockets();

        // Set the simulation state in the WebSocket handler
        //VehicleWebSocketHandler.setSimulationState(simulationState);

        // Define all the routes here
        setupRoutes();

        // Start Spark server
        init();

        System.out.println("Servidor de simulación iniciado en http://localhost:4567");
    }*/

    private void setupRoutes() {
        // Middleware para manejar CORS
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
            response.header("Access-Control-Allow-Origin", "*"); // Permitir todas las solicitudes
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        });

        // Endpoint de bienvenida
        get("/", (request, response) -> {
            response.type("text/html");
            return "<h1>Bienvenido al Servidor de Simulación de Vehículos</h1>" +
                    "<p>Para interactuar con la simulación, utiliza los endpoints adecuados.</p>";
        });

        // Endpoint para provocar una avería
        post("/breakdown", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");
            String breakdownType = request.queryParams("breakdownType");

            if (vehicleCode == null || breakdownType == null) {
                response.status(400);
                return "Parámetros faltantes: vehicleCode y breakdownType son obligatorios.";
            }

            // Validar el tipo de avería
            if (!isValidBreakdownType(breakdownType)) {
                response.status(400);
                return "Tipo de avería inválido. Debe ser 1, 2 o 3.";
            }

            // Provocar la avería en el estado de simulación
            simulationState.provocarAveria(vehicleCode, breakdownType);

            response.status(200);
            return "Avería procesada para el vehículo " + vehicleCode;
        });

        // Endpoint para obtener mensajes de averías
        get("/breakdown/messages", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            List<String> messages = simulationState.getBreakdownLogs().get(vehicleCode);

            if (messages == null || messages.isEmpty()) {
                response.status(404);
                return "No se encontraron mensajes de avería para el vehículo " + vehicleCode;
            }

            response.type("application/json");
            return gson.toJson(messages);
        });

        // Endpoint para obtener la posición de un vehículo
        get("/vehicle/position", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            Vehicle vehicle = simulationState.getVehicles().get(vehicleCode);
            if (vehicle == null) {
                response.status(404);
                return "No se encontró el vehículo con código " + vehicleCode;
            }

            Position position = vehicle.getCurrentPosition(simulationState.getCurrentTime());

            if (position == null) {
                response.status(500);
                return "No se pudo calcular la posición del vehículo " + vehicleCode;
            }

            // Crear Feature GeoJSON
            Map<String, Object> geoJsonFeature = new HashMap<>();
            geoJsonFeature.put("type", "Feature");

            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Point");
            geometry.put("coordinates", Arrays.asList(position.getLongitude(), position.getLatitude()));
            geoJsonFeature.put("geometry", geometry);

            Map<String, Object> properties = new HashMap<>();
            properties.put("vehicleCode", vehicleCode);
            geoJsonFeature.put("properties", properties);

            response.type("application/json");
            return gson.toJson(geoJsonFeature);
        });

        // Endpoint para obtener las posiciones de todos los vehículos
        get("/vehicles/positions", (request, response) -> {
            Map<String, Vehicle> vehicles = simulationState.getVehicles();
            LocalDateTime simulationTime = simulationState.getCurrentTime();
            Map<String, Location> locations = simulationState.getLocations();

            // Crear un FeatureCollection GeoJSON
            JsonObject featureCollection = new JsonObject();
            featureCollection.addProperty("type", "FeatureCollection");
            JsonArray features = new JsonArray();

            for (Map.Entry<String, Vehicle> entry : vehicles.entrySet()) {
                String vehicleCode = entry.getKey();
                Vehicle vehicle = entry.getValue();

                Position position = vehicle.getCurrentPosition(simulationTime);

                if (position != null) {
                    JsonObject feature = new JsonObject();
                    feature.addProperty("type", "Feature");

                    JsonObject geometry = new JsonObject();
                    geometry.addProperty("type", "Point");
                    JsonArray coordinates = new JsonArray();
                    coordinates.add(position.getLongitude());
                    coordinates.add(position.getLatitude());
                    geometry.add("coordinates", coordinates);
                    feature.add("geometry", geometry);

                    JsonObject properties = new JsonObject();
                    properties.addProperty("vehicleCode", vehicleCode);
                    // Puedes añadir más propiedades si lo deseas
                    feature.add("properties", properties);

                    features.add(feature);
                }
            }

            featureCollection.add("features", features);

            response.type("application/json");
            return gson.toJson(featureCollection);
        });

        // Endpoint para obtener las ubicaciones de oficinas y almacenes
        get("/locations", (request, response) -> {
            // Obtener instancia de LocationService
            LocationService locationService = LocationService.getInstance();
            Map<String, Location> allLocations = locationService.getAllLocations();

            // Lista de ubigeos que son almacenes principales
            List<String> almacenesPrincipales = Arrays.asList("150101", "040201", "130101"); // Lima, Arequipa, Trujillo

            // Crear una FeatureCollection GeoJSON
            JsonObject featureCollection = new JsonObject();
            featureCollection.addProperty("type", "FeatureCollection");
            JsonArray features = new JsonArray();

            for (Location loc : allLocations.values()) {
                JsonObject feature = new JsonObject();
                feature.addProperty("type", "Feature");

                JsonObject geometry = new JsonObject();
                geometry.addProperty("type", "Point");
                JsonArray coordinates = new JsonArray();
                coordinates.add(loc.getLongitude());
                coordinates.add(loc.getLatitude());
                geometry.add("coordinates", coordinates);
                feature.add("geometry", geometry);

                JsonObject properties = new JsonObject();
                properties.addProperty("name", loc.getProvince());
                properties.addProperty("ubigeo", loc.getUbigeo());
                // Diferenciar entre almacén y oficina
                if (almacenesPrincipales.contains(loc.getUbigeo())) {
                    properties.addProperty("type", "warehouse");
                } else {
                    properties.addProperty("type", "office");
                }
                feature.add("properties", properties);

                features.add(feature);
            }

            featureCollection.add("features", features);

            response.type("application/json");
            return gson.toJson(featureCollection);
        });

        // Endpoint para iniciar la simulación
        post("/simulation/start", (request, response) -> {
            if (isSimulationRunning) {
                response.status(400);
                return "La simulación ya está en ejecución.";
            }

            // Reiniciar el estado si estaba apagado
            if (isShutdown) {
                resetSimulationState();
            }

            startSimulation();
            response.status(200);
            return "Simulación iniciada.";
        });

        // Endpoint para pausar la simulación
        post("/simulation/pause", (request, response) -> {
            if (!isSimulationRunning) {
                response.status(400);
                return "La simulación no está en ejecución.";
            }
            pauseSimulation();
            response.status(200);
            return "Simulación pausada.";
        });

        // Endpoint para detener la simulación
        post("/simulation/stop", (request, response) -> {
            if (!isSimulationRunning) {
                response.status(400);
                return "La simulación no está en ejecución.";
            }
            stopSimulation();
            response.status(200);
            return "Simulación detenida.";
        });

        // Endpoint para obtener el historial de posiciones de un vehículo
        get("/vehicle/positions/history", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");

            if (vehicleCode == null) {
                response.status(400);
                return "Parámetro faltante: vehicleCode es obligatorio.";
            }

            Vehicle vehicle = simulationState.getVehicles().get(vehicleCode);
            if (vehicle == null) {
                response.status(404);
                return "No se encontró el vehículo con código " + vehicleCode;
            }

            List<Vehicle.PositionTimestamp> history = vehicle.getPositionHistory();

            if (history == null || history.isEmpty()) {
                response.status(404);
                return "No se encontró historial de posiciones para el vehículo " + vehicleCode;
            }

            // Convertir el historial a una lista de objetos JSON
            List<Map<String, Object>> historyJson = history.stream()
                    .map(pt -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("timestamp", pt.getTimestamp().toString());
                        map.put("latitude", pt.getPosition().getLatitude());
                        map.put("longitude", pt.getPosition().getLongitude());
                        return map;
                    })
                    .collect(Collectors.toList());

            response.type("application/json");
            return gson.toJson(historyJson);
        });

        // Manejo global de excepciones
        exception(Exception.class, (exception, request, response) -> {
            exception.printStackTrace();
            response.status(500);
            response.body("Error interno del servidor: " + exception.getMessage());
        });

        after((request, response) -> {
            System.out.println("Respuesta enviada para " + request.pathInfo() + " con estado " + response.status());
        });

    }

    // Método para configurar WebSockets
    private void configureWebSockets() {
        webSocket("/ws", VehicleWebSocketHandler.class);
    }

    // Método para validar el tipo de avería
    private boolean isValidBreakdownType(String breakdownType) {
        return breakdownType.equals("1") || breakdownType.equals("2") || breakdownType.equals("3");
    }

    private void resetSimulationState() {
        isShutdown = false;
        isSimulationRunning = false;
        if (simulationExecutor != null && !simulationExecutor.isShutdown()) {
            simulationExecutor.shutdownNow();
        }
        simulationExecutor = null;
        simulationFuture = null;
        simulationState.reset(); // Necesitas implementar este método en SimulationState
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
        // No cerramos el WebSocket ni su executor
    }

    private void startSimulation() {
        // Start the simulation in a separate thread
        isSimulationRunning = true;
        simulationExecutor = Executors.newSingleThreadExecutor();
        simulationFuture = simulationExecutor.submit(() -> {
            try {
                SimulationRunner.runSimulation(simulationState); // Use the full simulation logic
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isSimulationRunning = false;
            }
        });
    }

    private void runSimulation() {
        try {
            SimulationRunner.runSimulation(simulationState);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*private void runSimulation() {
        try {
            while (isSimulationRunning && !isShutdown) {
                simulationState.advanceTime();
                simulationState.updateVehicleStates();

                VehicleWebSocketHandler.broadcastVehiclePositions();

                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }*/

    private void pauseSimulation() {
        simulationState.pauseSimulation();
    }
}