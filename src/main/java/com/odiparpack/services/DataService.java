package com.odiparpack.services;

import com.odiparpack.DataLoader;
import com.odiparpack.models.*;

import java.util.List;
import java.util.Map;

public class DataService {
    private DataLoader dataLoader;

    public DataService() {
        this.dataLoader = new DataLoader();
    }

    public Map<String, Location> loadLocations(String filePath) {
        return dataLoader.loadLocations(filePath);
    }

    public List<Edge> loadEdges(String filePath, Map<String, Location> locations) {
        return dataLoader.loadEdges(filePath, locations);
    }

    public List<Vehicle> loadVehicles(String filePath) {
        return dataLoader.loadVehicles(filePath);
    }

    public List<Order> loadOrders(String filePath, Map<String, Location> locations) {
        return dataLoader.loadOrders(filePath, locations);
    }

    public List<Blockage> loadBlockages(String filePath) {
        return dataLoader.loadBlockages(filePath);
    }

    public List<Maintenance> loadMaintenanceSchedule(String filePath) {
        return dataLoader.loadMaintenanceSchedule(filePath);
    }

    public long[][] createTimeMatrix(List<Location> locationList, List<Edge> edges) {
        return dataLoader.createTimeMatrix(locationList, edges);
    }
}
