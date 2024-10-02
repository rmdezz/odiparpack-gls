package com.odiparpack.models;

import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.odiparpack.DataModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.odiparpack.Main.*;
import static com.odiparpack.Utils.calculateDistanceFromNodes;

public class SimulationState {
    private Map<String, Vehicle> vehicles;
    private List<Order> orders;
    private Map<String, Location> locations;
    private LocalDateTime currentTime;
    private ReentrantLock lock = new ReentrantLock();
    private static final int SIMULATION_SPEED = 10; // 1 minuto de simulación = 1 segundo de tiempo real
    private static final int PLANNING_INTERVAL_MINUTES = 15;
    private WarehouseManager warehouseManager;
    private List<Vehicle> vehiclesNeedingNewRoutes;
    private List<String> almacenesPrincipales = Arrays.asList("150101", "040201", "130101"); // Lima, Arequipa, Trujillo
    private RouteCache routeCache;
    private List<Blockage> activeBlockages;
    private long[][] currentTimeMatrix;
    private List<Maintenance> maintenanceSchedule;
    private static final String BREAKDOWN_COMMAND_FILE = "src/main/resources/breakdown_commands.txt";
    private long lastModified = 0;

    public List<Blockage> getActiveBlockages() {
        return activeBlockages;
    }

    public void setActiveBlockages(List<Blockage> activeBlockages) {
        this.activeBlockages = activeBlockages;
    }



    public long[][] getCurrentTimeMatrix() {
        return currentTimeMatrix;
    }

    public void setCurrentTimeMatrix(long[][] currentTimeMatrix) {
        this.currentTimeMatrix = currentTimeMatrix;
    }



    public SimulationState(Map<String, Vehicle> vehicleMap, LocalDateTime initialSimulationTime,
                           List<Order> orders, Map<String, Location> locations, RouteCache routeCache,
                           long[][] originalTimeMatrix, List<Blockage> blockages,
                           List<Maintenance> maintenanceSchedule) {
        this.vehicles = vehicleMap;
        this.currentTime = initialSimulationTime;
        this.orders = orders;
        this.warehouseManager = new WarehouseManager(locations);
        this.locations = locations;
        this.routeCache = routeCache;
        this.vehiclesNeedingNewRoutes = new ArrayList<>();
        this.activeBlockages = new ArrayList<>();
        this.currentTimeMatrix = Arrays.stream(originalTimeMatrix)
                .map(long[]::clone)
                .toArray(long[][]::new);
        this.maintenanceSchedule = maintenanceSchedule;
        updateBlockages(initialSimulationTime, blockages);
    }

    public WarehouseManager getWarehouseManager() {
        return warehouseManager;
    }

    public void updateBlockages(LocalDateTime currentTime, List<Blockage> allBlockages) {
        logger.info("Actualizando bloqueos en tiempo: " + currentTime);

        // Remover bloqueos que han expirado
        List<Blockage> expiredBlockages = activeBlockages.stream()
                .filter(blockage -> currentTime.isAfter(blockage.getEndTime()))
                .collect(Collectors.toList());

        for (Blockage expiredBlockage : expiredBlockages) {
            activeBlockages.remove(expiredBlockage);
            logger.info("Bloqueo expirado y removido: " + blockageToString(expiredBlockage));
        }

        // Añadir nuevos bloqueos activos
        int newBlockagesCount = 0;
        for (Blockage blockage : allBlockages) {
            if (!currentTime.isBefore(blockage.getStartTime()) &&
                    currentTime.isBefore(blockage.getEndTime()) &&
                    !activeBlockages.contains(blockage)) {
                activeBlockages.add(blockage);
                newBlockagesCount++;
                logger.info("Nuevo bloqueo activado: " + blockageToString(blockage));
            }
        }

        logger.info("Resumen de actualización de bloqueos:");
        logger.info("- Bloqueos expirados: " + expiredBlockages.size());
        logger.info("- Nuevos bloqueos activados: " + newBlockagesCount);
        logger.info("- Total de bloqueos activos: " + activeBlockages.size());

        // Actualizar la matriz de tiempo
        updateTimeMatrix();
    }

    private String blockageToString(Blockage blockage) {
        return String.format("Origen: %s, Destino: %s, Inicio: %s, Fin: %s",
                blockage.getOriginUbigeo(),
                blockage.getDestinationUbigeo(),
                blockage.getStartTime(),
                blockage.getEndTime());
    }

    public void checkForBreakdownCommands() {
        Path path = Paths.get(BREAKDOWN_COMMAND_FILE);
        try {
            long currentLastModified = Files.getLastModifiedTime(path).toMillis();
            if (currentLastModified > lastModified) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String vehicleCode = parts[0].trim();
                        String breakdownType = parts[1].trim();
                        provocarAveria(vehicleCode, breakdownType);
                    }
                }
                lastModified = currentLastModified;
                // Limpiar el archivo después de leer
                Files.write(path, new byte[0]);
            }
        } catch (IOException e) {
            logger.warning("Error al leer el archivo de comandos de avería: " + e.getMessage());
        }
    }

    private void updateTimeMatrix() {
        logger.info("Actualizando matriz de tiempo basada en bloqueos activos");

        // Restaurar la matriz original
        for (int i = 0; i < currentTimeMatrix.length; i++) {
            System.arraycopy(timeMatrix[i], 0, currentTimeMatrix[i], 0, timeMatrix[i].length);
        }

        // Aplicar bloqueos activos
        for (Blockage blockage : activeBlockages) {
            int fromIndex = locationIndices.get(blockage.getOriginUbigeo());
            int toIndex = locationIndices.get(blockage.getDestinationUbigeo());
            currentTimeMatrix[fromIndex][toIndex] = Long.MAX_VALUE;
            currentTimeMatrix[toIndex][fromIndex] = Long.MAX_VALUE; // Asumiendo que las rutas son bidireccionales
            logger.info("Ruta bloqueada: " + blockage.getOriginUbigeo() + " -> " + blockage.getDestinationUbigeo());
        }

        logger.info("Matriz de tiempo actualizada con " + activeBlockages.size() + " bloqueos aplicados");
    }

    public void updateVehicleStates(Map<String, List<RouteSegment>> vehicleRoutes) {
        lock.lock();
        try {
            List<Vehicle> vehiclesNeedingNewRoutes = new ArrayList<>();

            for (Vehicle vehicle : vehicles.values()) {
                // Primero, verificar si el vehículo está saliendo de mantenimiento
                if (vehicle.getEstado() == Vehicle.EstadoVehiculo.EN_MANTENIMIENTO) {
                    checkAndUpdateMaintenanceStatus(vehicle);
                }

                // Verificar si el vehiculo esta programado para mantenimiento
                Maintenance mantenimiento = getCurrentMaintenance(vehicle.getCode());
                if (mantenimiento != null) {
                    handleVehicleInMaintenance(vehicle, mantenimiento);
                } else if (vehicle.getEstado() == Vehicle.EstadoVehiculo.EN_TRANSITO_ORDEN ||
                        vehicle.getEstado() == Vehicle.EstadoVehiculo.HACIA_ALMACEN ||
                        vehicle.getEstado() == Vehicle.EstadoVehiculo.EN_ESPERA_EN_OFICINA) {
                    vehicle.updateStatus(currentTime, warehouseManager);
                } else if (vehicle.getEstado() == Vehicle.EstadoVehiculo.LISTO_PARA_RETORNO && !vehicle.isRouteBeingCalculated()) {
                    if (vehicle.getWaitStartTime() == null ||
                            ChronoUnit.MINUTES.between(vehicle.getWaitStartTime(), currentTime) >= Vehicle.WAIT_TIME_MINUTES) {
                        vehicle.setRouteBeingCalculated(true);
                        vehiclesNeedingNewRoutes.add(vehicle);
                    }
                }
            }

            if (!vehiclesNeedingNewRoutes.isEmpty()) {
                logger.info("Vehículos que necesitan nuevas rutas: " + vehiclesNeedingNewRoutes.stream()
                        .map(Vehicle::getCode)
                        .collect(Collectors.joining(", ")));

                // Ejecutar calculateNewRoutes en un hilo separado
                new Thread(() -> calculateNewRoutes(vehiclesNeedingNewRoutes)).start();
            }

        } finally {
            lock.unlock();
        }
    }

    private void checkAndUpdateMaintenanceStatus(Vehicle vehicle) {
        Maintenance lastMaintenance = maintenanceSchedule.stream()
                .filter(m -> m.getVehicleCode().equals(vehicle.getCode()) &&
                        m.getEndTime().isBefore(currentTime) || m.getEndTime().isEqual(currentTime))
                .max(Comparator.comparing(Maintenance::getEndTime))
                .orElse(null);

        if (lastMaintenance != null) {
            vehicle.setEstado(Vehicle.EstadoVehiculo.EN_ALMACEN);
            vehicle.setAvailable(true);
            logger.info("Vehículo " + vehicle.getCode() + " ha salido de mantenimiento y está disponible en " +
                    vehicle.getCurrentLocationUbigeo() + " a partir de " + currentTime);
        }
    }

    public void provocarAveria(String vehicleCode, String breakdownType) {
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
            } else {
                logger.warning(String.format("No se puede provocar avería en el vehículo %s porque no está en tránsito", vehicleCode));
            }
        } else {
            logger.warning(String.format("No se encontró el vehículo con código %s", vehicleCode));
        }
    }

    Maintenance getCurrentMaintenance(String code) {
        return maintenanceSchedule.stream()
                .filter(m -> m.getVehicleCode().equals(code) && m.isInMaintenancePeriod(currentTime))
                .findFirst()
                .orElse(null);
    }

    private void handleVehicleInMaintenance(Vehicle vehicle, Maintenance maintenance) {
        Vehicle.EstadoVehiculo estado = vehicle.getEstado();
        if (estado != Vehicle.EstadoVehiculo.EN_ALMACEN) {
            // El vehículo está en ruta o en proceso de ruta, debe completar la entrega antes de ir a mantenimiento
            logger.info("Vehículo " + vehicle.getCode() + " en ruta, irá a mantenimiento después de completar la entrega.");
        } else {
            // El vehículo no está en ruta, se envía directamente a mantenimiento
            vehicle.setEstado(Vehicle.EstadoVehiculo.EN_MANTENIMIENTO);
            vehicle.setAvailable(false);
            logger.info("Vehículo " + vehicle.getCode() + " enviado a mantenimiento hasta " + maintenance.getEndTime());
        }
    }

    private void calculateNewRoutes(List<Vehicle> vehicles) {
        Map<String, String> vehicleDestinations = new HashMap<>();
        Map<String, Map<String, Long>> vehicleRouteTimes = new HashMap<>();
        Set<RouteRequest> routesToCalculate = new HashSet<>();

        // Paso 1: Identificar rutas necesarias y buscar en caché
        for (Vehicle vehicle : vehicles) {
            String currentLocation = vehicle.getCurrentLocationUbigeo();
            vehicleRouteTimes.put(vehicle.getCode(), new HashMap<>());

            for (String warehouseUbigeo : almacenesPrincipales) {
                if (!warehouseUbigeo.equals(currentLocation)) {
                    List<RouteSegment> cachedRoute = routeCache.getRoute(warehouseUbigeo, currentLocation, activeBlockages);
                    if (cachedRoute != null) {
                        long routeTime = calculateRouteTime(cachedRoute);
                        vehicleRouteTimes.get(vehicle.getCode()).put(warehouseUbigeo, routeTime);
                    } else {
                        routesToCalculate.add(new RouteRequest(currentLocation, warehouseUbigeo));
                    }
                }
            }
        }

        // Paso 2: Calcular rutas faltantes
        if (!routesToCalculate.isEmpty()) {
            logger.info(String.format("Calculando %d rutas faltantes...", routesToCalculate.size()));
            Map<RouteRequest, List<RouteSegment>> calculatedRoutes = batchCalculateRoutes(routesToCalculate);

            for (Map.Entry<RouteRequest, List<RouteSegment>> entry : calculatedRoutes.entrySet()) {
                RouteRequest request = entry.getKey();
                List<RouteSegment> route = entry.getValue();
                long routeTime = calculateRouteTime(route);

                logger.info(String.format("Ruta calculada: Origen: %s, Destino: %s, Tiempo: %d minutos",
                        request.start, request.end, routeTime));

                for (Vehicle vehicle : vehicles) {
                    if (vehicle.getCurrentLocationUbigeo().equals(request.start)) {
                        vehicleRouteTimes.get(vehicle.getCode()).put(request.end, routeTime);
                        logger.info(String.format("Tiempo de ruta actualizado para vehículo %s: Destino %s, Tiempo: %d minutos",
                                vehicle.getCode(), request.end, routeTime));
                    }
                }

                routeCache.putRoute(request.end, request.start, route, activeBlockages);
                logger.info(String.format("Ruta almacenada en caché: Origen: %s, Destino: %s, Segmentos: %d",
                        request.start, request.end, route.size()));
            }
        } else {
            logger.info("No hay rutas faltantes para calcular.");
        }

        // Paso 3: Determinar el mejor destino para cada vehículo
        for (Vehicle vehicle : vehicles) {
            String bestDestination = findBestDestination(vehicle, vehicleRouteTimes.get(vehicle.getCode()));
            vehicleDestinations.put(vehicle.getCode(), bestDestination);

            String originUbigeo = vehicle.getCurrentLocationUbigeo();
            String originName = locations.get(originUbigeo).getProvince();
            String destinationName = locations.get(bestDestination).getProvince();

            logger.info(String.format("Vehículo %s en %s (%s) asignado al mejor destino %s (%s)",
                    vehicle.getCode(), originName, originUbigeo, destinationName, bestDestination));
        }

        // Paso 4: Asignar rutas a vehículos
        for (Vehicle vehicle : vehicles) {
            String destination = vehicleDestinations.get(vehicle.getCode());
            List<RouteSegment> route = routeCache.getRoute(vehicle.getCurrentLocationUbigeo(), destination, activeBlockages);
            if (route != null) {
                vehicle.setRoute(route);
                vehicle.startWarehouseJourney(currentTime, destination);
                logger.info(String.format("Vehículo %s asignado a ruta hacia %s (%s)", vehicle.getCode(), locations.get(destination).getProvince(), destination));

                // Imprimir los segmentos de la ruta
                StringBuilder routeDetails = new StringBuilder();
                routeDetails.append("Ruta asignada para vehículo ").append(vehicle.getCode()).append(":\n");
                for (int i = 0; i < route.size(); i++) {
                    RouteSegment segment = route.get(i);
                    routeDetails.append(String.format("%d. %s, Duración: %d minutos, Distancia: %.2f km\n",
                            i + 1, segment.getName(), segment.getDurationMinutes(), segment.getDistance()));
                }
                logger.info(routeDetails.toString());
            } else {
                logger.warning(String.format("No se pudo asignar ruta para el vehículo %s", vehicle.getCode()));
            }
            vehicle.setRouteBeingCalculated(false);
        }
    }

    private Map<RouteRequest, List<RouteSegment>> batchCalculateRoutes(Set<RouteRequest> routesToCalculate) {
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        for (RouteRequest request : routesToCalculate) {
            Integer startIndex = locationIndices.get(request.start);
            Integer endIndex = locationIndices.get(request.end);
            if (startIndex == null || endIndex == null) {
                logger.warning(String.format("Ubigeo de inicio o fin no encontrado en locationIndices: %s -> %s",
                        request.start, request.end));
            } else {
                starts.add(startIndex);
                ends.add(endIndex);
            }
        }

        DataModel data = new DataModel(getCurrentTimeMatrix(), getActiveBlockages(),
                starts.stream().mapToInt(Integer::intValue).toArray(),
                ends.stream().mapToInt(Integer::intValue).toArray(),
                locationNames, locationUbigeos);

        List<List<RouteSegment>> calculatedRoutes = calcularRutasHaciaAlmacen(data, data.starts, data.ends);

        if (calculatedRoutes.isEmpty()) {
            logger.warning("No se pudieron calcular rutas. 'calculatedRoutes' está vacío.");
            // Maneja este caso, por ejemplo, devolviendo un mapa vacío o lanzando una excepción controlada
            return Collections.emptyMap();
        }

        Map<RouteRequest, List<RouteSegment>> result = new HashMap<>();
        int index = 0;
        for (RouteRequest request : routesToCalculate) {
            if (index < calculatedRoutes.size()) {
                result.put(request, calculatedRoutes.get(index));
            } else {
                logger.warning(String.format("No hay ruta calculada para la solicitud %s -> %s", request.start, request.end));
            }
            index++;
        }

        return result;
    }

    private static List<List<RouteSegment>> calcularRutasHaciaAlmacen(DataModel data, int[] start, int[] end) {
        RoutingIndexManager manager = createRoutingIndexManager(data, start, end);
        RoutingModel routing = createRoutingModel(manager, data);
        RoutingSearchParameters searchParameters = createSearchParameters();

        logger.info("Iniciando la resolución del modelo de rutas para rutas hacia almacenes.");
        Assignment solution = routing.solveWithParameters(searchParameters);
        logger.info("Solución de rutas obtenida para rutas hacia almacenes.");

        if (solution != null) {
            List<List<RouteSegment>> calculatedRoutes = extractCalculatedRoutesWithoutAssignments(manager, data, routing, solution);
            printSolution(data, routing, manager, solution);
            logger.info("Solución de rutas hacia almacenes impresa correctamente.");
            return calculatedRoutes;
        } else {
            logger.warning("No se encontró solución para las rutas hacia almacenes.");
            // Imprimir detalles de las rutas que no pudieron ser calculadas
            for (int i = 0; i < start.length; i++) {
                String startUbigeo = data.locationUbigeos.get(start[i]);
                String endUbigeo = data.locationUbigeos.get(end[i]);
                String startName = data.locationNames.get(start[i]);
                String endName = data.locationNames.get(end[i]);
                logger.warning(String.format("No se pudo encontrar ruta desde %s (%s) hasta %s (%s).",
                        startName, startUbigeo, endName, endUbigeo));
            }
            return new ArrayList<>();
        }
    }


    private static List<List<RouteSegment>> extractCalculatedRoutesWithoutAssignments(
            RoutingIndexManager manager, DataModel data, RoutingModel routing, Assignment solution) {
        List<List<RouteSegment>> calculatedRoutes = new ArrayList<>();
        for (int i = 0; i < data.vehicleNumber; ++i) {
            List<RouteSegment> route = new ArrayList<>();
            long index = routing.start(i);
            while (!routing.isEnd(index)) {
                long nextIndex = solution.value(routing.nextVar(index));
                int fromNode = manager.indexToNode(index);
                int toNode = manager.indexToNode(nextIndex);

                String fromName = data.locationNames.get(fromNode);
                String fromUbigeo = data.locationUbigeos.get(fromNode);
                String toName = data.locationNames.get(toNode);
                String toUbigeo = data.locationUbigeos.get(toNode);

                long durationMinutes = data.timeMatrix[fromNode][toNode];
                double distance = calculateDistanceFromNodes(data, fromNode, toNode);

                route.add(new RouteSegment(fromName + " to " + toName, toUbigeo, distance, durationMinutes));

                index = nextIndex;
            }

            calculatedRoutes.add(route);
            logger.info("Ruta calculada para la ruta " + i + " con " + route.size() + " segmentos.");
        }
        return calculatedRoutes;
    }


    private long calculateRouteTime(List<RouteSegment> route) {
        return route.stream().mapToLong(RouteSegment::getDurationMinutes).sum();
    }

    private String findBestDestination(Vehicle vehicle, Map<String, Long> routeTimes) {
        return routeTimes.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static class RouteRequest {
        final String start;
        final String end;

        RouteRequest(String start, String end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteRequest that = (RouteRequest) o;
            return Objects.equals(start, that.start) && Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }

    private String findNearestWarehouse(Vehicle vehicle) {
        List<String> potentialDestinations = locations.values().stream()
                .filter(loc -> !loc.getUbigeo().equals(vehicle.getCurrentLocationUbigeo()))
                .map(Location::getUbigeo)
                .collect(Collectors.toList());

        String nearestWarehouse = null;
        long shortestRouteTime = Long.MAX_VALUE;

        for (String destination : potentialDestinations) {
            List<VehicleAssignment> singleAssignment = Collections.singletonList(new VehicleAssignment(vehicle, null, 0));
            DataModel data = new DataModel(timeMatrix, activeBlockages, singleAssignment, locationIndices, locationNames, locationUbigeos);
            int[] starts = {locationIndices.get(vehicle.getCurrentLocationUbigeo())};
            int[] ends = {locationIndices.get(destination)};

            Map<String, List<RouteSegment>> route = calculateRoute(data, starts, ends, this);
            List<RouteSegment> vehicleRoute = route.get(vehicle.getCode());

            if (vehicleRoute != null) {
                long routeTime = vehicleRoute.stream().mapToLong(RouteSegment::getDurationMinutes).sum();
                if (routeTime < shortestRouteTime) {
                    shortestRouteTime = routeTime;
                    nearestWarehouse = destination;
                }
            }
        }

        return nearestWarehouse;
    }

    private int[] getStartIndices(List<Vehicle> vehicles) {
        return vehicles.stream()
                .map(v -> locationIndices.get(v.getCurrentLocationUbigeo()))
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private int[] getEndIndices(String destination) {
        int endIndex = locationIndices.get(destination);
        return new int[vehicles.size()]; // Todos los vehículos van al mismo destino
    }


    public void updateOrderStatuses() {
        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.PENDING_PICKUP) {
                if (order.isReadyForDelivery(currentTime)) {
                    order.setDelivered(currentTime);
                    // Incrementar la capacidad del almacén de destino cuando el pedido se marca como entregado
                    warehouseManager.increaseCapacity(order.getDestinationUbigeo(), order.getQuantity());
                }
            }
        }
    }

    public void advanceTime() {
        currentTime = currentTime.plusMinutes(PLANNING_INTERVAL_MINUTES);
    }

    public LocalDateTime getCurrentTime() {
        lock.lock();
        try {
            return currentTime;
        } finally {
            lock.unlock();
        }
    }

    public void setCurrentTime(LocalDateTime currentTime) {
        lock.lock();
        try {
            this.currentTime = currentTime;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Vehicle> getVehicles() {
        return vehicles;
    }
}
