package com.odiparpack.api.controller.routers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.odiparpack.models.Location;
import com.odiparpack.services.LocationService;
import spark.Spark;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class LocationRouter extends BaseRouter {
    private final LocationService locationService;
    private final List<String> almacenesPrincipales = Arrays.asList("150101", "040201", "130101");

    public LocationRouter() {
        this.locationService = LocationService.getInstance();
    }

    @Override
    public void setupRoutes() {
        System.out.println("Configurando rutas de LocationRouter");

        // Usando métodos estáticos de Spark
        Spark.get("/api/v1/locations/info", (request, response) -> {
            response.type("application/json");
            return getLocationsInfo();
        });

        // Endpoint para información geográfica de ubicaciones (GeoJSON)
        Spark.get("/api/v1/locations", (request, response) -> {
            response.type("application/json");
            return getLocationsGeoJSON();
        });
    }

    private JsonObject getLocationsInfo() {
        JsonObject response = new JsonObject();
        JsonArray locationsList = new JsonArray();

        for (Map.Entry<String, Location> entry : locationService.getAllLocations().entrySet()) {
            Location loc = entry.getValue();
            JsonObject locationInfo = new JsonObject();

            locationInfo.addProperty("province", loc.getProvince());
            locationInfo.addProperty("ubigeo", loc.getUbigeo());
            boolean isWarehouse = almacenesPrincipales.contains(loc.getUbigeo());
            locationInfo.addProperty("type", isWarehouse ? "WAREHOUSE" : "OFFICE");

            if (!isWarehouse) {
                locationInfo.addProperty("maxCapacity", loc.getWarehouseCapacity());
            }

            locationsList.add(locationInfo);
        }

        response.add("locations", locationsList);
        return response;
    }

    private JsonObject getLocationsGeoJSON() {
        JsonObject featureCollection = new JsonObject();
        featureCollection.addProperty("type", "FeatureCollection");
        JsonArray features = new JsonArray();

        for (Location loc : locationService.getAllLocations().values()) {
            JsonObject feature = new JsonObject();
            feature.addProperty("type", "Feature");

            // Agregar geometría
            JsonObject geometry = new JsonObject();
            geometry.addProperty("type", "Point");
            JsonArray coordinates = new JsonArray();
            coordinates.add(loc.getLongitude());
            coordinates.add(loc.getLatitude());
            geometry.add("coordinates", coordinates);
            feature.add("geometry", geometry);

            // Agregar propiedades
            JsonObject properties = new JsonObject();
            properties.addProperty("name", loc.getProvince());
            properties.addProperty("ubigeo", loc.getUbigeo());
            properties.addProperty("type", almacenesPrincipales.contains(loc.getUbigeo()) ? "warehouse" : "office");

            // Si es oficina, agregar capacidad
            if (!almacenesPrincipales.contains(loc.getUbigeo())) {
                properties.addProperty("maxCapacity", loc.getWarehouseCapacity());
            }

            feature.add("properties", properties);
            features.add(feature);
        }

        featureCollection.add("features", features);
        return featureCollection;
    }
}