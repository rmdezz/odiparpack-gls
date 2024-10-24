package com.odiparpack.simulation.state;

import com.odiparpack.models.*;
import com.odiparpack.services.DataService;
import com.odiparpack.simulation.blockage.BlockageManager;
import com.odiparpack.simulation.maintenance.MaintenanceManager;
import com.odiparpack.simulation.order.OrderManager;
import com.odiparpack.simulation.route.RouteManager;
import com.odiparpack.simulation.vehicle.VehicleManager;

import java.time.LocalDateTime;
import java.util.*;

/**
 * La clase SimulationInitializer se encarga de inicializar los componentes de la simulación
 * a partir de los datos cargados por DataService.
 */
public class SimulationInitializer {
    private final DataService dataService;

    public SimulationInitializer(DataService dataService) {
        this.dataService = dataService;
    }

    public SimulationComponents initializeComponents() {
        Map<String, Location> locations = dataService.loadLocations("src/main/resources/locations.txt");
        List<Edge> edges = dataService.loadEdges("src/main/resources/edges.txt", locations);
        List<Vehicle> vehiclesList = dataService.loadVehicles("src/main/resources/vehicles.txt");
        List<Order> orders = dataService.loadOrders("src/main/resources/orders.txt", locations);
        List<Blockage> blockages = dataService.loadBlockages("src/main/resources/blockages.txt");
        List<Maintenance> maintenanceSchedule = dataService.loadMaintenanceSchedule("src/main/resources/maintenance.txt");

        List<Location> locationList = new ArrayList<>(locations.values());
        Map<String, Integer> locationIndices = createLocationIndices(locationList);
        long[][] timeMatrix = dataService.createTimeMatrix(locationList, edges);
        List<String> locationNames = createLocationNames(locationList);
        List<String> locationUbigeos = createLocationUbigeos(locationList);
        Map<String, Vehicle> vehicles = createVehicleMap(vehiclesList);
        LocalDateTime initialTime = getInitialSimulationTime(orders);

        // Inicializar los managers
        WarehouseManager warehouseManager = new WarehouseManager(locations);
        MaintenanceManager maintenanceManager = new MaintenanceManager(maintenanceSchedule);
        BlockageManager blockageManager = new BlockageManager(blockages, timeMatrix, locationIndices);
        RouteManager routeManager = new RouteManager(new RouteCache(1000), locationIndices, locationNames,
                locationUbigeos, blockageManager);
        OrderManager orderManager = new OrderManager(orders);
        VehicleManager vehicleManager = new VehicleManager(vehicles, warehouseManager, routeManager, maintenanceManager);

        return new SimulationComponents(initialTime, vehicleManager, orderManager, routeManager, blockageManager, maintenanceManager, warehouseManager);
    }

    // Métodos auxiliares para crear estructuras de datos
    private Map<String, Integer> createLocationIndices(List<Location> locationList) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < locationList.size(); i++) {
            indices.put(locationList.get(i).getUbigeo(), i);
        }
        return indices;
    }

    private List<String> createLocationNames(List<Location> locationList) {
        List<String> names = new ArrayList<>();
        for (Location loc : locationList) {
            names.add(loc.getProvince());
        }
        return names;
    }

    private List<String> createLocationUbigeos(List<Location> locationList) {
        List<String> ubigeos = new ArrayList<>();
        for (Location loc : locationList) {
            ubigeos.add(loc.getUbigeo());
        }
        return ubigeos;
    }

    private Map<String, Vehicle> createVehicleMap(List<Vehicle> vehiclesList) {
        Map<String, Vehicle> vehicles = new HashMap<>();
        for (Vehicle v : vehiclesList) {
            vehicles.put(v.getCode(), v);
        }
        return vehicles;
    }

    private LocalDateTime getInitialSimulationTime(List<Order> orders) {
        return orders.stream()
                .map(Order::getOrderTime)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }
}
