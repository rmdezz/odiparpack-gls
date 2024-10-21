/*
package com.odiparpack;

import com.odiparpack.DataLoader;
import com.odiparpack.Main;
import com.odiparpack.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

@Component
public class SimulationRunner {

    private static final Logger logger = Logger.getLogger(SimulationRunner.class.getName());

    @Autowired
    private DataLoader dataLoader;

    @Autowired
    private RouteCache routeCache;

    public void runSimulation() {
        // Cargar datos
        Map<String, Location> locations = dataLoader.loadLocations("src/main/resources/locations.txt");
        List<Edge> edges = dataLoader.loadEdges("src/main/resources/edges.txt", locations);
        List<Vehicle> vehicles = dataLoader.loadVehicles("src/main/resources/vehicles.txt");
        List<Order> orders = dataLoader.loadOrders("src/main/resources/orders.txt", locations);
        List<Blockage> blockages = dataLoader.loadBlockages("src/main/resources/blockages.txt");
        List<Maintenance> maintenanceSchedule = dataLoader.loadMaintenanceSchedule("src/main/resources/maintenance.txt");

        // Inicializar datos
        List<Location> locationList = new ArrayList<>(locations.values());
        Map<String, Integer> locationIndices = new HashMap<>();
        for (int i = 0; i < locationList.size(); i++) {
            locationIndices.put(locationList.get(i).getUbigeo(), i);
        }

        long[][] timeMatrix = dataLoader.createTimeMatrix(locationList, edges);

        List<String> locationNames = new ArrayList<>();
        List<String> locationUbigeos = new ArrayList<>();
        for (Location loc : locationList) {
            locationNames.add(loc.getProvince());
            locationUbigeos.add(loc.getUbigeo());
        }

        // Iniciar simulación
        runSimulation(timeMatrix, orders, vehicles, locationIndices, locationNames, locationUbigeos, locations, routeCache,
                blockages, maintenanceSchedule);
    }

    private void runSimulation(long[][] timeMatrix, List<Order> allOrders, List<Vehicle> vehicles,
                               Map<String, Integer> locationIndices, List<String> locationNames,
                               List<String> locationUbigeos, Map<String, Location> locations,
                               RouteCache routeCache, List<Blockage> blockages, List<Maintenance> maintenanceSchedule) {
        SimulationState state = Main.initializeSimulation(allOrders, vehicles, locations, routeCache, timeMatrix, blockages, maintenanceSchedule, locationIndices, locationNames, locationUbigeos);
        Map<String, List<RouteSegment>> vehicleRoutes = new HashMap<>();
        ScheduledExecutorService executorService = Main.setupExecutors();

        try {
            Main.runSimulationLoop(state, timeMatrix, allOrders, locationIndices, locationNames, locationUbigeos,
                    vehicleRoutes, executorService, blockages);
        } catch (InterruptedException e) {
            logger.severe("Simulación interrumpida: " + e.getMessage());
        } finally {
            Main.shutdownExecutors(executorService);
        }
    }
}*/
