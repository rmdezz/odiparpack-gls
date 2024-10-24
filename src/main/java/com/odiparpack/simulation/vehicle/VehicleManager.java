package com.odiparpack.simulation.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.odiparpack.models.*;
import com.odiparpack.models.WarehouseManager;
import com.odiparpack.simulation.maintenance.MaintenanceManager;
import com.odiparpack.simulation.order.OrderManager;
import com.odiparpack.simulation.route.RouteManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * La clase VehicleManager gestiona los vehículos, incluyendo sus estados,
 * asignación de rutas y manejo de averías.
 */
public class VehicleManager {
    private static final Logger logger = Logger.getLogger(VehicleManager.class.getName());

    private final Map<String, Vehicle> vehicles;
    private final WarehouseManager warehouseManager;
    private final RouteManager routeManager;
    private final MaintenanceManager maintenanceManager;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<String> mainWarehouses = Arrays.asList("150101", "040201", "130101"); // Lima, Arequipa, Trujillo
    private static final Map<String, List<String>> breakdownLogs = new HashMap<>();

    /**
     * Constructor de VehicleManager.
     *
     * @param vehicles           Mapa de vehículos gestionados.
     * @param warehouseManager   Instancia de WarehouseManager.
     * @param routeManager       Instancia de RouteManager.
     * @param maintenanceManager Instancia de MaintenanceManager.
     */
    public VehicleManager(Map<String, Vehicle> vehicles, WarehouseManager warehouseManager,
                          RouteManager routeManager, MaintenanceManager maintenanceManager) {
        this.vehicles = vehicles;
        this.warehouseManager = warehouseManager;
        this.routeManager = routeManager;
        this.maintenanceManager = maintenanceManager;
    }

    // Método para obtener los logs de averías de un vehículo específico
    public List<String> getBreakdownLogs(String vehicleCode) {
        return breakdownLogs.getOrDefault(vehicleCode, Collections.emptyList());
    }

    /**
     * Actualiza los estados de los vehículos en función del tiempo actual.
     *
     * @param currentTime El tiempo actual de la simulación.
     */
    public void updateVehicleStates(LocalDateTime currentTime, long[][] timeMatrix) {
        lock.lock();
        try {
            List<Vehicle> vehiclesNeedingNewRoutes = new ArrayList<>();

            for (Vehicle vehicle : vehicles.values()) {
                if (maintenanceManager.isVehicleUnderMaintenance(vehicle, currentTime)) {
                    maintenanceManager.handleVehicleInMaintenance(vehicle, currentTime);
                    continue;
                }

                if (vehicle.isUnderRepair()) {
                    maintenanceManager.updateBreakdownTime(vehicle, currentTime);
                    vehicle.handleRepairCompletion(currentTime);
                }

                if (vehicle.shouldUpdateStatus()) {
                    vehicle.updateStatus(currentTime, warehouseManager);
                }

                if (vehicle.shouldCalculateNewRoute(currentTime)) {
                    vehiclesNeedingNewRoutes.add(vehicle);
                }
            }

            if (!vehiclesNeedingNewRoutes.isEmpty()) {
                logVehiclesNeedingNewRoutes(vehiclesNeedingNewRoutes);
                processNewRoutes(vehiclesNeedingNewRoutes, currentTime, timeMatrix);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Registra los vehículos que necesitan nuevas rutas.
     *
     * @param vehiclesNeedingNewRoutes Lista de vehículos que necesitan nuevas rutas.
     */
    private void logVehiclesNeedingNewRoutes(List<Vehicle> vehiclesNeedingNewRoutes) {
        String vehicleCodes = vehiclesNeedingNewRoutes.stream()
                .map(Vehicle::getCode)
                .collect(Collectors.joining(", "));
        logger.info("Vehículos que necesitan nuevas rutas: " + vehicleCodes);
    }

    /**
     * Procesa la asignación de nuevas rutas a los vehículos.
     *
     * @param vehiclesNeedingNewRoutes Lista de vehículos que necesitan nuevas rutas.
     * @param currentTime              El tiempo actual de la simulación.
     */
    private void processNewRoutes(List<Vehicle> vehiclesNeedingNewRoutes, LocalDateTime currentTime, long[][] timeMatrix) {
        new Thread(() -> {
            Map<Vehicle, List<RouteSegment>> calculatedRoutes = routeManager.calculateRoutesToWarehouses(vehiclesNeedingNewRoutes, mainWarehouses, timeMatrix);
            for (Vehicle vehicle : vehiclesNeedingNewRoutes) {
                List<RouteSegment> route = calculatedRoutes.get(vehicle);
                if (route != null && !route.isEmpty()) {
                    vehicle.setRoute(route);
                    vehicle.startWarehouseJourney(currentTime, route.get(route.size() - 1).getToUbigeo());
                    logger.info(String.format("Vehículo %s asignado a ruta hacia %s", vehicle.getCode(), route.get(route.size() - 1).getToUbigeo()));
                } else {
                    logger.warning(String.format("No se pudo asignar ruta para el vehículo %s", vehicle.getCode()));
                }
            }
        }).start();
    }

    /**
     * Obtiene una lista de vehículos disponibles para asignaciones.
     *
     * @return Lista de vehículos disponibles.
     */
    public List<Vehicle> getAvailableVehicles() {
        lock.lock();
        try {
            return vehicles.values().stream()
                    .filter(Vehicle::isAvailable)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Asigna rutas a los vehículos basándose en las asignaciones proporcionadas.
     *
     * @param assignments Lista de asignaciones de vehículos.
     * @param currentTime El tiempo actual de la simulación.
     */
    public void assignRoutesToVehicles(List<VehicleAssignment> assignments, LocalDateTime currentTime, long[][] timeMatrix) {
        lock.lock();
        try {
            for (VehicleAssignment assignment : assignments) {
                Vehicle vehicle = assignment.getVehicle();
                Order order = assignment.getOrder();

                // Obtener la ruta desde el origen hasta el destino de la orden
                List<RouteSegment> route = routeManager.calculateRouteForAssignment(vehicle, order, timeMatrix);
                if (route != null && !route.isEmpty()) {
                    vehicle.setRoute(route);
                    vehicle.startJourney(currentTime, order);
                    logger.info(String.format("Vehículo %s asignado a ruta para entregar orden %d", vehicle.getCode(), order.getId()));
                } else {
                    logger.warning(String.format("No se pudo calcular ruta para el vehículo %s y la orden %d", vehicle.getCode(), order.getId()));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Provoca una avería en un vehículo específico.
     *
     * @param vehicleCode    Código del vehículo.
     * @param breakdownType  Tipo de avería ("1", "2", "3").
     * @param currentTime    El tiempo actual de la simulación.
     */
    public void provocarAveria(String vehicleCode, String breakdownType, LocalDateTime currentTime) {
        lock.lock();
        try {
            Vehicle vehicle = vehicles.get(vehicleCode);
            if (vehicle != null) {
                if (vehicle.getEstado() == Vehicle.EstadoVehiculo.EN_TRANSITO_ORDEN) {
                    Vehicle.EstadoVehiculo estadoAveria;
                    switch (breakdownType) {
                        case "1":
                            estadoAveria = Vehicle.EstadoVehiculo.AVERIADO_1;
                            break;
                        case "2":
                            estadoAveria = Vehicle.EstadoVehiculo.AVERIADO_2;
                            break;
                        case "3":
                            estadoAveria = Vehicle.EstadoVehiculo.AVERIADO_3;
                            break;
                        default:
                            logger.warning("Tipo de avería no reconocido: " + breakdownType);
                            return;
                    }
                    vehicle.handleBreakdown(currentTime, estadoAveria);
                    logger.info(String.format("Avería tipo %s provocada en el vehículo %s", breakdownType, vehicleCode));
                    // Agregar un mensaje de avería
                    String logMessage = String.format("Avería tipo %s provocada en el vehículo %s en %s.",
                            breakdownType, vehicleCode, currentTime);
                    addBreakdownLog(vehicleCode, logMessage);
                } else {
                    logger.warning(String.format("No se puede provocar avería en el vehículo %s porque no está en tránsito", vehicleCode));
                }
            } else {
                logger.warning(String.format("No se encontró el vehículo con código %s", vehicleCode));
            }
        } finally {
            lock.unlock();
        }
    }

    // Método para agregar un mensaje de avería
    public static void addBreakdownLog(String vehicleCode, String message) {
        breakdownLogs.computeIfAbsent(vehicleCode, k -> new ArrayList<>()).add(message);
    }

    /**
     * Obtiene las posiciones actuales de los vehículos en formato GeoJSON.
     *
     * @param currentTime El tiempo actual de la simulación.
     * @return Objeto JSON representando las posiciones de los vehículos.
     */
    public JsonObject getCurrentPositionsGeoJSON(LocalDateTime currentTime) {
        JsonObject featureCollection = new JsonObject();
        featureCollection.addProperty("type", "FeatureCollection");
        JsonArray features = new JsonArray();

        lock.lock();
        try {
            for (Vehicle vehicle : vehicles.values()) {
                Position position = vehicle.getCurrentPosition(currentTime);
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
                    properties.addProperty("vehicleCode", vehicle.getCode());
                    // Puedes añadir más propiedades si lo deseas
                    feature.add("properties", properties);

                    features.add(feature);
                }
            }
        } finally {
            lock.unlock();
        }

        featureCollection.add("features", features);
        return featureCollection;
    }

    public static List<Vehicle> getAvailableVehicles(List<Vehicle> vehicles, String locationUbigeo) {
        // Loguear el origen del ubigeo de la orden antes del filtrado
        logger.info(String.format("Ubigeo de origen de la orden: %s", locationUbigeo));

        return vehicles.stream()
                .peek(v -> logger.info(String.format("Ubigeo actual del vehículo %s: %s", v.getCode(), v.getCurrentLocationUbigeo())))
                .filter(v -> v.getEstado() == Vehicle.EstadoVehiculo.EN_ALMACEN && v.getCurrentLocationUbigeo().equals(locationUbigeo))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el mapa de vehículos gestionados.
     *
     * @return Mapa de vehículos.
     */
    public Map<String, Vehicle> getVehicles() {
        return vehicles;
    }
}
