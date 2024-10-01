package com.odiparpack.models;

import java.util.HashMap;
import java.util.Map;

import static com.odiparpack.Main.logger;

public class WarehouseManager {
    private Map<String, Integer> warehouseCapacities;
    private Map<String, Integer> currentCapacities;

    public WarehouseManager(Map<String, Location> locations) {
        warehouseCapacities = new HashMap<>();
        currentCapacities = new HashMap<>();
        for (Location location : locations.values()) {
            warehouseCapacities.put(location.getUbigeo(), location.getWarehouseCapacity());
            currentCapacities.put(location.getUbigeo(), location.getWarehouseCapacity());
        }
    }

    public void decreaseCapacity(String ubigeo, int amount) {
        int currentCapacity = currentCapacities.getOrDefault(ubigeo, 0);
        int newCapacity = currentCapacity - amount;
        currentCapacities.put(ubigeo, newCapacity);
        logCapacityChange(ubigeo, currentCapacity, newCapacity);
    }

    public void increaseCapacity(String ubigeo, int amount) {
        int currentCapacity = currentCapacities.getOrDefault(ubigeo, 0);
        int newCapacity = currentCapacity + amount;
        currentCapacities.put(ubigeo, newCapacity);
        logCapacityChange(ubigeo, currentCapacity, newCapacity);
    }

    private void logCapacityChange(String ubigeo, int oldCapacity, int newCapacity) {
        int totalCapacity = warehouseCapacities.getOrDefault(ubigeo, 0);
        logger.info(String.format("Almacén %s: Capacidad total: %d, Capacidad anterior: %d, Nueva capacidad: %d",
                ubigeo, totalCapacity, oldCapacity, newCapacity));
        if (newCapacity <= 0) {
            logger.warning(String.format("¡ADVERTENCIA! La capacidad del almacén %s ha llegado a %d", ubigeo, newCapacity));
        }
    }

    public int getCurrentCapacity(String ubigeo) {
        return currentCapacities.getOrDefault(ubigeo, 0);
    }
}