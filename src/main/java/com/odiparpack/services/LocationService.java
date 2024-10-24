package com.odiparpack.services;

import com.odiparpack.models.Location;
import com.odiparpack.DataLoader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LocationService {
    private static final Logger logger = Logger.getLogger(LocationService.class.getName());
    private static LocationService instance;
    private Map<String, Location> locations = new ConcurrentHashMap<>();

    private LocationService() {
        loadLocations();
    }

    // Método para obtener la instancia única de LocationService (singleton)
    public static synchronized LocationService getInstance() {
        if (instance == null) {
            instance = new LocationService();
        }
        return instance;
    }

    // Cargar ubicaciones desde el archivo utilizando DataLoader
    private void loadLocations() {
        DataLoader dataLoader = new DataLoader();
        this.locations = dataLoader.loadLocations("src/main/resources/locations.txt");
        logger.info("Ubicaciones cargadas: " + locations.size());
    }

    // Obtener una ubicación por ubigeo
    public Location getLocation(String ubigeo) {
        return locations.get(ubigeo);
    }

    // Obtener todas las ubicaciones
    public Map<String, Location> getAllLocations() {
        return locations;
    }
}
