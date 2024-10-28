package com.odiparpack.api.controller.routers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.odiparpack.models.Position;
import com.odiparpack.models.SimulationState;
import com.odiparpack.models.Vehicle;
import spark.Spark;

import java.util.List;
import java.util.Map;

public class VehicleRouter extends BaseRouter {
    private final SimulationState simulationState;

    public VehicleRouter(SimulationState simulationState) {
        this.simulationState = simulationState;
    }

    @Override
    public void setupRoutes() {
        // Ruta para obtener posición de un vehículo específico
        Spark.get("/api/v1/vehicles/position/:code", (request, response) -> {
            response.type("application/json");
            String vehicleCode = request.params(":code");
            return getVehiclePosition(vehicleCode);
        });

        // Ruta para obtener todas las posiciones
        Spark.get("/api/v1/vehicles/positions", (request, response) -> {
            response.type("application/json");
            return getAllVehiclePositions();
        });

        // Ruta para provocar averías
        Spark.post("/api/v1/vehicles/breakdown", (request, response) -> {
            String vehicleCode = request.queryParams("vehicleCode");
            String breakdownType = request.queryParams("breakdownType");

            if (vehicleCode == null || breakdownType == null) {
                response.status(400);
                return "{\"error\": \"vehicleCode y breakdownType son requeridos\"}";
            }

            simulationState.provocarAveria(vehicleCode, breakdownType);
            return "{\"message\": \"Avería provocada exitosamente\"}";
        });

        // Ruta para obtener historial de averías
        Spark.get("/api/v1/vehicles/breakdown/history/:code", (request, response) -> {
            response.type("application/json");
            String vehicleCode = request.params(":code");
            List<String> logs = simulationState.getBreakdownLogs().get(vehicleCode);

            if (logs == null || logs.isEmpty()) {
                response.status(404);
                return "{\"error\": \"No hay historial de averías para este vehículo\"}";
            }

            return toJson(logs);
        });
    }

    private JsonObject getVehiclePosition(String vehicleCode) {
        Vehicle vehicle = simulationState.getVehicles().get(vehicleCode);
        if (vehicle == null) {
            return createErrorResponse("Vehículo no encontrado");
        }

        Position position = vehicle.getCurrentPosition(simulationState.getCurrentTime());
        if (position == null) {
            return createErrorResponse("Posición no disponible");
        }

        return createPositionResponse(vehicleCode, position);
    }

    private JsonObject getAllVehiclePositions() {
        JsonObject featureCollection = new JsonObject();
        featureCollection.addProperty("type", "FeatureCollection");
        JsonArray features = new JsonArray();

        for (Map.Entry<String, Vehicle> entry : simulationState.getVehicles().entrySet()) {
            Position position = entry.getValue().getCurrentPosition(simulationState.getCurrentTime());
            if (position != null) {
                features.add(createFeature(entry.getKey(), position));
            }
        }

        featureCollection.add("features", features);
        return featureCollection;
    }

    private JsonObject createFeature(String vehicleCode, Position position) {
        JsonObject feature = new JsonObject();
        feature.addProperty("type", "Feature");

        JsonObject geometry = new JsonObject();
        geometry.addProperty("type", "Point");
        JsonArray coordinates = new JsonArray();
        coordinates.add(position.getLongitude());
        coordinates.add(position.getLatitude());
        geometry.add("coordinates", coordinates);

        JsonObject properties = new JsonObject();
        properties.addProperty("vehicleCode", vehicleCode);

        feature.add("geometry", geometry);
        feature.add("properties", properties);

        return feature;
    }

    private JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }

    private JsonObject createPositionResponse(String vehicleCode, Position position) {
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
        feature.add("properties", properties);

        return feature;
    }
}
