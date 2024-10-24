package com.odiparpack;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.odiparpack.models.*;
import com.odiparpack.websocket.VehicleWebSocketHandler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.odiparpack.Main.locationIndices;
import static com.odiparpack.Main.routeCache;
import static com.odiparpack.Utils.calculateDistanceFromNodes;
import static com.odiparpack.Utils.formatTime;

public class SimulationRunner {
    private static final Logger logger = Logger.getLogger(SimulationRunner.class.getName());
    private static final int SIMULATION_DAYS = 7;
    private static final int SIMULATION_SPEED = 10; // 1 minuto de simulación = 1 segundo de tiempo real
    private static final int PLANNING_INTERVAL_MINUTES = 15;
    private static final int TIME_ADVANCEMENT_INTERVAL_MINUTES = 5;
    private static ScheduledExecutorService simulationExecutorService;
    private static ScheduledExecutorService webSocketExecutorService;

    public static void runSimulation(SimulationState state) throws InterruptedException {
        // Obtener los datos necesarios del estado de simulación
        long[][] timeMatrix = state.getCurrentTimeMatrix();
        List<Order> allOrders = state.getOrders();
        Map<String, Integer> locationIndices = state.getLocationIndices();
        List<String> locationNames = state.getLocationNames();
        List<String> locationUbigeos = state.getLocationUbigeos();
        Map<String, List<RouteSegment>> vehicleRoutes = new HashMap<>();

        LocalDateTime endTime = state.getCurrentTime().plusDays(SIMULATION_DAYS);
        AtomicBoolean isSimulationRunning = new AtomicBoolean(true);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 2);
        simulationExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        if (webSocketExecutorService == null || webSocketExecutorService.isShutdown()) {
            webSocketExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduleWebSocketBroadcast(state, isSimulationRunning);
        }

        try {
            // Iniciar tareas programadas
            scheduleTimeAdvancement(state, endTime, isSimulationRunning, vehicleRoutes, executorService);
            schedulePlanning(state, allOrders, locationIndices, locationNames, locationUbigeos, vehicleRoutes, executorService, isSimulationRunning);

            while (!state.isStopped() && isSimulationRunning.get()) {
                if (state.isPaused()) {
                    Thread.sleep(1000);
                    continue;
                }
                // Realizar otras tareas si es necesario
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Simulación interrumpida", e);
            throw e;
        } finally {
            simulationExecutorService.shutdownNow();
        }
    }

    public static void stopWebSocketBroadcast() {
        if (webSocketExecutorService != null && !webSocketExecutorService.isShutdown()) {
            webSocketExecutorService.shutdownNow();
            webSocketExecutorService = null;
        }
    }

    private static void scheduleWebSocketBroadcast(SimulationState state, AtomicBoolean isSimulationRunning) {
        webSocketExecutorService.scheduleAtFixedRate(() -> {
            try {
                if (state.isPaused() || state.isStopped()) return;

                // Broadcast vehicle positions via WebSocket
                VehicleWebSocketHandler.broadcastVehiclePositions();

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in WebSocket broadcast task", e);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }



    private static void scheduleTimeAdvancement(SimulationState state, LocalDateTime endTime, AtomicBoolean isSimulationRunning,
                                                Map<String, List<RouteSegment>> vehicleRoutes,
                                                ScheduledExecutorService executorService) {
        executorService.scheduleAtFixedRate(() -> {
            try {
                if (!isSimulationRunning.get() || state.isPaused() || state.isStopped()) return;

                state.setCurrentTime(state.getCurrentTime().plusMinutes(TIME_ADVANCEMENT_INTERVAL_MINUTES));
                logger.info("Tiempo de simulación: " + state.getCurrentTime());

                state.updateBlockages(state.getCurrentTime(), state.getAllBlockages());
                state.updateVehicleStates();
                state.updateOrderStatuses();
                logger.info("Estados de vehículos, pedidos y bloqueos actualizados.");

                if (state.getCurrentTime().isAfter(endTime)) {
                    logger.info("Simulación completada.");
                    isSimulationRunning.set(false);
                    state.stopSimulation();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error en la tarea de avance del tiempo", e);
            }
        }, 0, TIME_ADVANCEMENT_INTERVAL_MINUTES * 1000L / SIMULATION_SPEED, TimeUnit.MILLISECONDS);
    }

    private static void schedulePlanning(SimulationState state, List<Order> allOrders,
                                         Map<String, Integer> locationIndices, List<String> locationNames,
                                         List<String> locationUbigeos, Map<String, List<RouteSegment>> vehicleRoutes,
                                         ScheduledExecutorService executorService, AtomicBoolean isSimulationRunning) {
        executorService.scheduleAtFixedRate(() -> {
            if (!isSimulationRunning.get() || state.isPaused() || state.isStopped()) return;

            logger.info("Iniciando algoritmo de planificación en tiempo de simulación: " + state.getCurrentTime());

            try {
                long[][] currentTimeMatrix = state.getCurrentTimeMatrix();
                List<Order> availableOrders = getAvailableOrders(allOrders, state.getCurrentTime());
                logAvailableOrders(availableOrders);

                if (!availableOrders.isEmpty()) {
                    List<VehicleAssignment> assignments = assignOrdersToVehicles(availableOrders, new ArrayList<>(state.getVehicles().values()), state.getCurrentTime());
                    if (!assignments.isEmpty()) {
                        calculateAndApplyRoutes(currentTimeMatrix, assignments, locationIndices, locationNames,
                                locationUbigeos, vehicleRoutes, state, executorService);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error en el ciclo de planificación", e);
            }
        }, 0, PLANNING_INTERVAL_MINUTES * 1000L / SIMULATION_SPEED, TimeUnit.MILLISECONDS);
    }

    private static List<Order> getAvailableOrders(List<Order> allOrders, LocalDateTime currentTime) {
        return allOrders.stream()
                .filter(order -> (order.getStatus() == Order.OrderStatus.REGISTERED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ASSIGNED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ARRIVED)
                        && !order.getOrderTime().isAfter(currentTime))
                .collect(Collectors.toList());
    }

    private static void logAvailableOrders(List<Order> availableOrders) {
        logger.info("Órdenes disponibles: " + availableOrders.size());
        for (Order order : availableOrders) {
            logger.info("Orden " + order.getId() + " - Paquetes restantes sin asignar: " + order.getUnassignedPackages());
        }
    }

    private static List<VehicleAssignment> assignOrdersToVehicles(List<Order> orders, List<Vehicle> vehicles, LocalDateTime currentTime) {
        List<VehicleAssignment> assignments = new ArrayList<>();

        // Ordenar los pedidos por dueTime (los más urgentes primero)
        orders.sort(Comparator.comparing(Order::getDueTime));

        for (Order order : orders) {
            if (order.getStatus() != Order.OrderStatus.REGISTERED &&
                    order.getStatus() != Order.OrderStatus.PARTIALLY_ASSIGNED &&
                    order.getStatus() != Order.OrderStatus.PARTIALLY_ARRIVED) {
                logger.info("Orden " + order.getId() + " no está en estado REGISTERED, PARTIALLY_ASSIGNED o PARTIALLY_ARRIVED. Se omite.");
                continue;
            }

            int unassignedPackages = order.getUnassignedPackages();
            if (unassignedPackages <= 0) {
                continue; // No hay paquetes por asignar
            }

            List<Vehicle> availableVehicles = getAvailableVehicles(vehicles, order.getOriginUbigeo()).stream()
                    .sorted(Comparator.comparingInt(Vehicle::getCapacity).reversed()) // Ordenar por capacidad descendente
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                logger.info("No hay vehículos disponibles en " + order.getOriginUbigeo() + " para la orden " + order.getId());
                continue;
            }

            for (Vehicle vehicle : availableVehicles) {
                if (unassignedPackages <= 0) {
                    break; // No hay más paquetes que asignar
                }

                if (vehicle.getCapacity() >= unassignedPackages) {
                    // El vehículo puede satisfacer completamente la orden
                    assignments.add(new VehicleAssignment(vehicle, order, unassignedPackages));
                    vehicle.setAvailable(false);
                    vehicle.setEstado(Vehicle.EstadoVehiculo.ORDENES_CARGADAS);
                    order.incrementAssignedPackages(unassignedPackages); // Actualización completa

                    String logMessage = String.format(
                            "\n--- Asignación Completa ---\n" +
                                    "Código de la Orden: %d\n" +
                                    "Cantidad Total de la Orden: %d paquetes\n" +
                                    "Cantidad Asignada al Vehículo: %d paquetes\n" +
                                    "Código del Vehículo: %s\n" +
                                    "---------------------------",
                            order.getId(),
                            order.getQuantity(),
                            unassignedPackages,
                            vehicle.getCode()
                    );
                    logger.info(logMessage);

                    //order.setAssignedPackages(unassignedPackages);
                    unassignedPackages = 0;
                    break;
                } else if (vehicle.getCapacity() > 0) {
                    // El vehículo puede satisfacer parcialmente la orden
                    int assignedQuantity = Math.min(vehicle.getCapacity(), unassignedPackages); // Limitar a paquetes restantes
                    assignments.add(new VehicleAssignment(vehicle, order, assignedQuantity));
                    vehicle.setAvailable(false);
                    vehicle.setEstado(Vehicle.EstadoVehiculo.ORDENES_CARGADAS);
                    order.incrementAssignedPackages(assignedQuantity); // Actualización parcial

                    String logMessage = String.format(
                            "\n--- Asignación Parcial ---\n" +
                                    "Código de la Orden: %d\n" +
                                    "Cantidad Total de la Orden: %d paquetes\n" +
                                    "Cantidad Asignada al Vehículo: %d paquetes\n" +
                                    "Código del Vehículo: %s\n" +
                                    "---------------------------",
                            order.getId(),
                            order.getQuantity(),
                            assignedQuantity,
                            vehicle.getCode()
                    );
                    logger.info(logMessage);

                    unassignedPackages -= assignedQuantity;
                }
            }

            if (unassignedPackages > 0) {
                order.setStatus(Order.OrderStatus.PARTIALLY_ASSIGNED);
                logger.warning("Quedan " + unassignedPackages + " paquetes por asignar para la orden " + order.getId());
            } else {
                order.setStatus(Order.OrderStatus.FULLY_ASSIGNED);
                logger.info("Orden " + order.getId() + " completamente asignada.");
            }
        }

        return assignments;
    }

    private static List<Vehicle> getAvailableVehicles(List<Vehicle> vehicles, String locationUbigeo) {
        // Loguear el origen del ubigeo de la orden antes del filtrado
        logger.info(String.format("Ubigeo de origen de la orden: %s", locationUbigeo));

        return vehicles.stream()
                .peek(v -> logger.info(String.format("Ubigeo actual del vehículo %s: %s", v.getCode(), v.getCurrentLocationUbigeo())))
                .filter(v -> v.getEstado() == Vehicle.EstadoVehiculo.EN_ALMACEN && v.getCurrentLocationUbigeo().equals(locationUbigeo))
                .collect(Collectors.toList());
    }

    private static void calculateAndApplyRoutes(long[][] currentTimeMatrix, List<VehicleAssignment> assignments,
                                                Map<String, Integer> locationIndices, List<String> locationNames,
                                                List<String> locationUbigeos, Map<String, List<RouteSegment>> vehicleRoutes,
                                                SimulationState state, ExecutorService executorService) {
        if (locationIndices == null || locationIndices.isEmpty()) {
            logger.severe("locationIndices no está inicializado.");
            return;
        }

        DataModel data = new DataModel(currentTimeMatrix, state.getActiveBlockages(), assignments, locationIndices, locationNames, locationUbigeos);
        executorService.submit(() -> {
            try {
                Map<String, List<RouteSegment>> newRoutes = calculateRoute(data, data.starts, data.ends, state);
                vehicleRoutes.putAll(newRoutes);
                logger.info("Nuevas rutas calculadas y agregadas en tiempo de simulación: " + state.getCurrentTime());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error durante el cálculo de rutas", e);
            }
        });
    }

    public static Map<String, List<RouteSegment>> calculateRoute(DataModel data, int[] start, int[] end, SimulationState state) {
        logger.info("\n--- Inicio del cálculo de rutas ---");
        Map<String, List<RouteSegment>> allRoutes = new HashMap<>();

        try {
            Map<String, List<RouteSegment>> cachedRoutes = getCachedRoutes(data, start, end);
            allRoutes.putAll(cachedRoutes);

            if (cachedRoutes.size() < data.vehicleNumber) {
                logger.info("Se necesitan calcular rutas adicionales. Rutas en caché: " + cachedRoutes.size() + ", Vehículos totales: " + data.vehicleNumber);
                Map<String, List<RouteSegment>> calculatedRoutes = calculateMissingRoutes(data, start, end, cachedRoutes); // y almacena en cache
                allRoutes.putAll(calculatedRoutes);
                //updateRouteCache(data, start, end, calculatedRoutes);
            } else {
                logger.info("Todas las rutas fueron encontradas en caché.");
                logAllCachedRoutes(cachedRoutes);
            }

            applyRoutesToVehicles(data, allRoutes, state);

            return allRoutes;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error durante el cálculo de rutas.", e);
            return allRoutes;
        } finally {
            logger.info("--- Fin del cálculo de rutas ---\n");
        }
    }

    private static Map<String, List<RouteSegment>> getCachedRoutes(DataModel data, int[] start, int[] end) {
        Map<String, List<RouteSegment>> cachedRoutes = new HashMap<>();
        for (int i = 0; i < data.vehicleNumber; i++) {
            String fromUbigeo = data.locationUbigeos.get(start[i]);
            String toUbigeo = data.locationUbigeos.get(end[i]);
            String vehicleCode = data.assignments.get(i).getVehicle().getCode();

            List<RouteSegment> cachedRoute = routeCache.getRoute(fromUbigeo, toUbigeo, data.activeBlockages);
            if (cachedRoute != null) {
                cachedRoutes.put(vehicleCode, cachedRoute);
                logCachedRoute(vehicleCode, fromUbigeo, toUbigeo, cachedRoute);
            } else {
                logger.info("Ruta no encontrada en caché para vehículo " + vehicleCode + ": " + fromUbigeo + " -> " + toUbigeo);
            }
        }
        return cachedRoutes;
    }

    private static void logCachedRoute(String vehicleCode, String fromUbigeo, String toUbigeo, List<RouteSegment> route) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n--- Ruta encontrada en caché ---\n");
        logBuilder.append("Código del Vehículo: ").append(vehicleCode).append("\n");
        logBuilder.append("Origen (Ubigeo): ").append(fromUbigeo).append("\n");
        logBuilder.append("Destino (Ubigeo): ").append(toUbigeo).append("\n");
        logBuilder.append("Segmentos de la ruta:\n");
        double totalDuration = 0;
        for (int i = 0; i < route.size(); i++) {
            RouteSegment segment = route.get(i);
            totalDuration += segment.getDurationMinutes();
            logBuilder.append("  ").append(i + 1).append(". ")
                    .append("Nombre: ").append(segment.getName())
                    .append(", Ubigeo: ").append(segment.getUbigeo())
                    .append(", Distancia: ").append(segment.getDistance()).append(" km")
                    .append(", Duración: ").append(segment.getDurationMinutes()).append(" minutos\n");
        }
        logBuilder.append("Duración total de la ruta: ").append(totalDuration).append(" minutos\n");
        logBuilder.append("-----------------------------");
        logger.info(logBuilder.toString());
    }

    private static Map<String, List<RouteSegment>> calculateMissingRoutes(DataModel data, int[] start, int[] end,
                                                                          Map<String, List<RouteSegment>> existingRoutes) {
        // Crear una nueva DataModel solo con las rutas que faltan
        DataModel missingData = createMissingDataModel(data, start, end, existingRoutes);
        RoutingIndexManager manager = createRoutingIndexManager(missingData, missingData.starts, missingData.ends);
        RoutingModel routing = createRoutingModel(manager, missingData);
        RoutingSearchParameters searchParameters = createSearchParameters();

        logger.info("Verifiquemos otra vez el tramo LUYA - BONGARA");
        missingData.printTravelTime("010501", "010301");

        logger.info("Iniciando la resolución del modelo de rutas para rutas faltantes.");
        Assignment solution = routing.solveWithParameters(searchParameters);
        logger.info("Solución de rutas obtenida para rutas faltantes.");

        if (solution != null) {
            Map<String, List<RouteSegment>> calculatedRoutes = extractCalculatedRoutes(data.activeBlockages, manager, missingData, missingData.assignments, routing, solution);
            //Map<String, List<RouteSegment>> calculatedRoutes = applyRouteToVehicles(manager, missingData, missingData.assignments, routing, solution, state);
            printSolution(missingData, routing, manager, solution);
            logger.info("Solución de rutas faltantes impresa correctamente.");
            return calculatedRoutes;
        } else {
            logger.warning("No se encontró solución para las rutas faltantes.");
            return new HashMap<>();
        }
    }

    private static Map<String, List<RouteSegment>> extractCalculatedRoutes(List<Blockage> activeBlockages, RoutingIndexManager manager, DataModel data,
                                                                           List<VehicleAssignment> assignments,
                                                                           RoutingModel routing, Assignment solution) {
        Map<String, List<RouteSegment>> calculatedRoutes = new HashMap<>();
        for (int i = 0; i < assignments.size(); ++i) {
            VehicleAssignment assignment = assignments.get(i);
            Vehicle vehicle = assignment.getVehicle();

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

                route.add(new RouteSegment(fromName + " to " + toName, fromUbigeo, toUbigeo, distance, durationMinutes));

                index = nextIndex;
            }

            calculatedRoutes.put(vehicle.getCode(), route);

            // Añadir la ruta calculada al caché
            routeCache.putRoute(data.locationUbigeos.get(data.starts[i]),
                    data.locationUbigeos.get(data.ends[i]),
                    route,
                    activeBlockages);

            logger.info("Ruta calculada para el vehículo " + vehicle.getCode() + " con " + route.size() + " segmentos.");
        }
        return calculatedRoutes;
    }

    public static RoutingSearchParameters createSearchParameters() {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.CHRISTOFIDES)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(10).build())
                //.setLogSearch(true)  // Habilitar "verbose logging"
                .build();
        logger.info("Parámetros de búsqueda configurados.");
        return searchParameters;
    }

    public static RoutingModel createRoutingModel(RoutingIndexManager manager, DataModel data) {
        RoutingModel routing = new RoutingModel(manager);
        logger.info("RoutingModel creado.");

        final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return data.timeMatrix[fromNode][toNode];
        });
        logger.info("Callback de tránsito registrado: " + transitCallbackIndex);

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
        logger.info("Evaluador de costo de arco establecido para todos los vehículos.");

        routing.addDimension(transitCallbackIndex, 0, Integer.MAX_VALUE, true, "Time");
        logger.info("Dimensión 'Time' agregada.");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        timeDimension.setGlobalSpanCostCoefficient(100);
        logger.info("Coeficiente de costo global de 'Time' establecido.");

        addSoftPenalties(routing, manager, data);

        return routing;
    }

    private static void addSoftPenalties(RoutingModel routing, RoutingIndexManager manager, DataModel data) {
        for (int i = 0; i < data.timeMatrix.length; i++) {
            if (!isStartOrEndNode(i, data.starts, data.ends)) {
                routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, 100);
            }
        }
    }

    private static boolean isStartOrEndNode(int node, int[] starts, int[] ends) {
        for (int i = 0; i < starts.length; i++) {
            if (node == starts[i] || node == ends[i]) {
                return true;
            }
        }
        return false;
    }

    public static RoutingIndexManager createRoutingIndexManager(DataModel data, int[] start, int[] end) {
        RoutingIndexManager manager = new RoutingIndexManager(
                data.timeMatrix.length,
                data.vehicleNumber,
                start,
                end);
        logger.info("RoutingIndexManager creado.");
        return manager;
    }

    private static void logAllCachedRoutes(Map<String, List<RouteSegment>> cachedRoutes) {
        logger.info("\n--- Rutas encontradas en caché ---");
        for (Map.Entry<String, List<RouteSegment>> entry : cachedRoutes.entrySet()) {
            String vehicleCode = entry.getKey();
            List<RouteSegment> route = entry.getValue();
            logger.info("Vehículo " + vehicleCode + ":");
            for (int i = 0; i < route.size(); i++) {
                RouteSegment segment = route.get(i);
                logger.info(String.format("  Segmento %d: %s -> %s, Duración: %d minutos, Distancia: %.2f km",
                        i + 1, segment.getName().split(" to ")[0], segment.getName().split(" to ")[1],
                        segment.getDurationMinutes(), segment.getDistance()));
            }
        }
        logger.info("-------------------------------");
    }

    private static void applyRoutesToVehicles(DataModel data, Map<String, List<RouteSegment>> allRoutes, SimulationState state) {
        for (VehicleAssignment assignment : data.assignments) {
            Vehicle vehicle = assignment.getVehicle();
            List<RouteSegment> route = allRoutes.get(vehicle.getCode());
            if (route != null) {
                vehicle.setRoute(route);
                if (state != null) {
                    vehicle.startJourney(state.getCurrentTime(), assignment.getOrder());
                }
                logger.info("Vehículo " + vehicle.getCode() + " iniciando viaje a " + assignment.getOrder().getDestinationUbigeo());
            } else {
                logger.warning("No se encontró ruta para el vehículo " + vehicle.getCode());
            }
        }
    }

    public static DataModel createMissingDataModel(DataModel originalData, int[] start, int[] end, Map<String, List<RouteSegment>> existingRoutes) {
        List<VehicleAssignment> missingAssignments = new ArrayList<>();
        List<Integer> missingStarts = new ArrayList<>();
        List<Integer> missingEnds = new ArrayList<>();

        for (int i = 0; i < originalData.vehicleNumber; i++) {
            String vehicleCode = originalData.assignments.get(i).getVehicle().getCode();
            if (!existingRoutes.containsKey(vehicleCode)) {
                missingAssignments.add(originalData.assignments.get(i));
                missingStarts.add(start[i]);
                missingEnds.add(end[i]);
            }
        }

        int[] newStarts = missingStarts.stream().mapToInt(Integer::intValue).toArray();
        int[] newEnds = missingEnds.stream().mapToInt(Integer::intValue).toArray();

        logger.info("Verifiquemos el valor del tramo LUYA - BONGARA");
        return new DataModel(
                originalData.timeMatrix,
                originalData.activeBlockages,
                missingAssignments,
                locationIndices,
                originalData.locationNames,
                originalData.locationUbigeos
        );
    }

    public static void printSolution(
            DataModel data, RoutingModel routing, RoutingIndexManager manager, Assignment solution) {
        // Objetivo de la solución.
        logger.info("Objetivo de la Solución: " + solution.objectiveValue());

        // Inspeccionar la solución.
        long maxRouteTime = 0;
        long localTotalTime = 0;  // Variable local para almacenar el tiempo total de esta solución

        for (int i = 0; i < data.vehicleNumber; ++i) {
            long index = routing.start(i);
            logger.info("\n--- Ruta para el Vehículo " + i + " ---");
            long routeTime = 0;
            StringBuilder routeBuilder = new StringBuilder();
            int routeStep = 1;
            while (!routing.isEnd(index)) {
                long previousIndex = index;
                index = solution.value(routing.nextVar(index));

                int fromNode = manager.indexToNode(previousIndex);
                int toNode = manager.indexToNode(index);

                String fromLocationName = data.locationNames.get(fromNode);
                String fromLocationUbigeo = data.locationUbigeos.get(fromNode);
                String toLocationName = data.locationNames.get(toNode);
                String toLocationUbigeo = data.locationUbigeos.get(toNode);

                long arcCost = routing.getArcCostForVehicle(previousIndex, index, i);
                long dimCost = solution.min(routing.getMutableDimension("Time").cumulVar(index)) -
                        solution.min(routing.getMutableDimension("Time").cumulVar(previousIndex));
                long matrixTime = data.timeMatrix[fromNode][toNode];

                routeTime += matrixTime;

                /*route.append(String.format(
                        "%d. De %s (%s) a %s (%s): %s\n",
                        routeStep,
                        fromLocationName, fromLocationUbigeo,
                        toLocationName, toLocationUbigeo,
                        formatTime(matrixTime)
                ));*/
                /*logger.info(String.format("  ArcCost: %d, DimCost: %d, MatrixTime: %d",
                        arcCost, dimCost, matrixTime));*/

                // Formatear el tiempo de duración
                String formattedDuration = formatTime(matrixTime);

                routeBuilder.append(String.format(
                        "%d. De %s (%s) a %s (%s): %s\n",
                        routeStep,
                        fromLocationName, fromLocationUbigeo,
                        toLocationName, toLocationUbigeo,
                        formattedDuration
                ));

                routeStep++;
            }

            logger.info(routeBuilder.toString());
            logger.info("Tiempo total de la ruta: " + formatTime(routeTime));
            maxRouteTime = Math.max(routeTime, maxRouteTime);
            localTotalTime += routeTime;
        }
        logger.info("Máximo tiempo de las rutas: " + formatTime(maxRouteTime));
        //totalDeliveryTime = localTotalTime;
    }

    // Métodos auxiliares como getAvailableOrders, logAvailableOrders, assignOrdersToVehicles, calculateAndApplyRoutes
    // deben ser implementados o referenciados desde la clase Main o Utils.

    // Implementa los métodos auxiliares aquí o ajusta según tu estructura de paquetes.
}
