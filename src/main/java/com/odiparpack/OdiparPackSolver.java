/*
package com.odiparpack;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.odiparpack.models.*;
import com.google.ortools.Loader;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class OdiparPackSolver {
    static {
        Loader.loadNativeLibraries();
    }

    private final Map<String, Location> locations;
    private final List<Order> allOrders;
    private final List<Vehicle> vehicles;
    private final List<Edge> edges;
    private final List<Blockage> blockages;
    private final List<Maintenance> maintenances;

    private RoutingIndexManager manager;
    private RoutingModel routing;

    // Lista ordenada de Ubigeos
    private List<String> ubigeoList;
    private Map<String, Integer> ubigeoToNodeIndex;

    // Definir la unidad de tiempo
    private static final int TIME_UNIT = 60; // 1 minuto

    // Variables de simulación
    private long simulationStartTime;
    private long simulationCurrentTime;
    private long simulationEndTime;
    private static final long SIMULATION_DURATION = 7 * 24 * 60L; // 1 semana en minutos
    private static final long SIMULATION_STEP = 60L; // Ciclo de planificación cada 1 hora en minutos
    private static final double SIMULATION_SPEED = 1.0; // Velocidad de simulación

    private int[][] timeMatrix;

    // Estados de vehículos y pedidos
    private List<VehicleState> vehicleStates;
    private Map<Integer, OrderState> orderStates;

    public OdiparPackSolver(Map<String, Location> locations, List<Order> orders, List<Vehicle> vehicles,
                            List<Edge> edges, List<Blockage> blockages, List<Maintenance> maintenances) {
        this.locations = locations;
        this.allOrders = orders;
        this.vehicles = vehicles;
        this.edges = edges;
        this.blockages = blockages;
        this.maintenances = maintenances;

        // Inicializar el tiempo de simulación
        this.simulationStartTime = 0L;
        this.simulationCurrentTime = 0L; // Tiempo relativo inicia en cero
        this.simulationEndTime = SIMULATION_DURATION; // En minutos

        System.out.println("Número de edges cargados: " + edges.size());
        printLoadedEdges(); // Llama al método que creamos anteriormente

        // Inicializar estados
        initializeStates();
    }

    private void initializeStates() {
        // Inicializar el estado de los vehículos
        vehicleStates = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            vehicleStates.add(new VehicleState(vehicle, vehicle.getCurrentLocationUbigeo(),
                    simulationCurrentTime, false, vehicle.getHomeUbigeo()));
        }

        // Inicializar el estado de los pedidos
        orderStates = new HashMap<>();
        for (Order order : allOrders) {
            orderStates.put(order.getId(), new OrderState(order, false));
        }
    }

    public void runSimulation() {
        preprocessEdges();

        while (simulationCurrentTime <= simulationEndTime) {
            System.out.println("Tiempo simulado actual: " + formatMinutes(simulationCurrentTime));

            // Verificar si hay nuevos pedidos en este tiempo
            for (Order order : allOrders) {
                if (order.getOrderTime() == simulationCurrentTime) {
                    System.out.println("Nuevo pedido recibido - ID: " + order.getId() +
                            " en tiempo: " + formatMinutes(simulationCurrentTime));
                }
            }

            List<Order> activeOrders = getActiveOrders();

            if (!activeOrders.isEmpty()) {
                createUbigeoListAndMapping(activeOrders);
                int numLocations = ubigeoList.size();
                timeMatrix = createTimeMatrix(numLocations);
                solve(activeOrders);
            } else {
                System.out.println("No hay pedidos activos en este ciclo.");
            }

            // Actualizar el estado de los vehículos en cada ciclo
            updateVehicleStates();

            simulationCurrentTime += SIMULATION_STEP;
            System.out.println();
        }

        System.out.println("Simulación completada: Se ha alcanzado el límite de tiempo.");
    }

    private void updateVehicleStates() {
        for (VehicleState state : vehicleStates) {
            if (state.isInTransit()) {
                if (state.getAvailableTime() <= simulationCurrentTime) {
                    state.setInTransit(false);
                    System.out.println("Vehículo " + state.getVehicle().getCode() +
                            " ha llegado a " + state.getCurrentUbigeo() +
                            " en tiempo: " + formatMinutes(simulationCurrentTime));
                }
            }
        }
    }

    private List<Order> validateOrders(List<Order> activeOrders) {
        List<Order> validOrders = new ArrayList<>();
        for (Order order : activeOrders) {
            String originUbigeo = order.getOriginUbigeo();
            String destinationUbigeo = order.getDestinationUbigeo();

            // Claves únicas para pickup y delivery
            String pickupKey = originUbigeo + "_pickup_" + order.getId();
            String deliveryKey = destinationUbigeo + "_delivery_" + order.getId();

            Integer pickupIndex = ubigeoToNodeIndex.get(pickupKey);
            Integer deliveryIndex = ubigeoToNodeIndex.get(deliveryKey);

            // Imprimir para ver si se encuentran correctamente las ubicaciones de pickup y delivery
            System.out.println("Validando pedido ID: " + order.getId());
            System.out.println("Ubigeo de pickup: " + pickupKey + " (índice: " + pickupIndex + ")");
            System.out.println("Ubigeo de delivery: " + deliveryKey + " (índice: " + deliveryIndex + ")");

            if (pickupIndex == null || deliveryIndex == null) {
                System.out.println("Error: No se encontró la ubicación de pickup o delivery para el pedido " + order.getId());
                continue;
            }

            int travelTime = timeMatrix[pickupIndex][deliveryIndex];
            System.out.println("Tiempo de viaje para el pedido " + order.getId() + ": " + travelTime + " minutos");

            if (travelTime >= Integer.MAX_VALUE / 2) {
                System.out.println("Colapso logístico: No hay ruta disponible para el pedido " + order.getId());
                continue;
            }

            long orderTimeRelative = order.getOrderTime(); // Ya en minutos relativos
            long dueTimeRelative = order.getDueTime();     // Ya en minutos relativos

            long earliestDeparture = Math.max(orderTimeRelative, simulationCurrentTime);
            long latestArrival = dueTimeRelative;

            if (earliestDeparture + travelTime > latestArrival) {
                System.out.println("Colapso logístico: No es posible entregar el pedido " + order.getId() + " dentro de la ventana de tiempo.");
                continue;
            }

            // Verificar capacidad de los vehículos
            boolean capacityAvailable = false;
            System.out.println("Capacidades y disponibilidad de vehículos para el pedido ID " + order.getId() + ":");
            for (VehicleState vehicleState : vehicleStates) {
                Vehicle vehicle = vehicleState.getVehicle();
                String vehicleInfo = "Vehículo: " + vehicle.getCode() +
                        " | Capacidad: " + vehicle.getCapacity() +
                        " | En tránsito: " + vehicleState.isInTransit() +
                        " | Ubicación actual: " + vehicleState.getCurrentUbigeo() +
                        " | Tiempo disponible: " + formatMinutes(vehicleState.getAvailableTime());

                if (!vehicleState.isInTransit() && vehicle.getCapacity() >= order.getQuantity()) {
                    capacityAvailable = true;
                    vehicleInfo += " [Disponible para el pedido]";
                }

                System.out.println(vehicleInfo);
            }

            if (!capacityAvailable) {
                System.out.println("Advertencia: No hay vehículos disponibles con capacidad suficiente para el pedido " + order.getId());
                continue;
            }

            System.out.println("Pedido ID " + order.getId() + " es válido y será considerado.");
            validOrders.add(order);
        }
        return validOrders;
    }

    private String formatMinutes(long totalMinutes) {
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" días, ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append(" horas, ");
        }
        sb.append(minutes).append(" minutos");
        return sb.toString();
    }

    private List<Order> getActiveOrders() {
        List<Order> activeOrders = new ArrayList<>();
        long currentTime = simulationCurrentTime;
        for (Order order : allOrders) {
            OrderState orderState = orderStates.get(order.getId());
            if (!orderState.isAssigned() && order.getOrderTime() <= currentTime && order.getDueTime() >= currentTime) {
                System.out.println("Activando pedido ID: " + order.getId() + " en tiempo: " + formatMinutes(currentTime));
                activeOrders.add(order);
            }
        }
        return activeOrders;
    }

    private void printTimeMatrix() {
        System.out.println("Matriz de Tiempos (en minutos):");
        for (int i = 0; i < timeMatrix.length; i++) {
            for (int j = 0; j < timeMatrix[i].length; j++) {
                if (timeMatrix[i][j] >= Integer.MAX_VALUE / 2) {
                    System.out.print("INF\t");
                } else {
                    System.out.print(timeMatrix[i][j] + "\t");
                }
            }
            System.out.println();
        }
    }

    public void solve(List<Order> activeOrders) {
        // Validar pedidos activos y obtener solo los válidos
        List<Order> validOrders = validateOrders(activeOrders);
        if (validOrders.isEmpty()) {
            System.out.println("No hay pedidos válidos para planificar en este ciclo.");
            return;
        }

        // Filtrar vehículos disponibles
        List<Vehicle> availableVehicles = getAvailableVehicles();
        if (availableVehicles.isEmpty()) {
            System.out.println("No hay vehículos disponibles en este ciclo. Se procederá a reubicar vehículos.");
            triggerVehicleRelocation();
            return;
        }

        // Crear el modelo de enrutamiento
        createRoutingModel(validOrders, availableVehicles);

        // Configurar parámetros de búsqueda
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(60).build())
                .build();

        // Resolver el problema
        System.out.println("Resolviendo el problema, por favor espere...");
        long startTime = System.currentTimeMillis();
        Assignment solution = routing.solveWithParameters(searchParameters);
        long endTime = System.currentTimeMillis();
        long timeTaken = endTime - startTime;

        System.out.println("Tiempo tomado para resolver el problema: " + timeTaken + " milisegundos.");

        if (solution != null) {
            List<VehicleRoute> vehicleRoutes = printSolution(routing, manager, solution, validOrders, availableVehicles);
            updateVehicleStatesWithNewRoutes(vehicleRoutes);
            updateOrderStates(validOrders);
            System.out.println("Problema resuelto exitosamente.");
        } else {
            System.out.println("No se encontró una solución factible para los pedidos activos en este ciclo.");
        }
    }

    private void createRoutingModel(List<Order> validOrders, List<Vehicle> availableVehicles) {
        // Cada pedido tendrá dos nodos: pickup y delivery
        int numOrders = validOrders.size();
        int numLocations = ubigeoList.size();
        int totalNodes = numLocations + 2 * numOrders; // Ubigeos existentes + pickups + deliveries
        int numVehicles = availableVehicles.size();

        // Crear arrays para pickups y deliveries
        int[] starts = new int[numVehicles];
        int[] ends = new int[numVehicles];

        for (int i = 0; i < numVehicles; i++) {
            Vehicle vehicle = availableVehicles.get(i);
            VehicleState state = getVehicleState(vehicle.getCode());
            starts[i] = ubigeoToNodeIndex.get(state.getCurrentUbigeo());
            ends[i] = ubigeoToNodeIndex.get(state.getHomeUbigeo());
        }

        // Crear el RoutingIndexManager con el número total de nodos
        manager = new RoutingIndexManager(totalNodes, numVehicles, starts, ends);

        // Crear el RoutingModel
        routing = new RoutingModel(manager);

        // Registrar el callback de tiempo de tránsito
        int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return calculateTravelTime(fromNode, toNode);
        });

        // Establecer el evaluador de costo de arco
        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        // Añadir la dimensión de tiempo
        routing.addDimension(
                transitCallbackIndex,
                30, // slack
                24 * 60, // máximo tiempo de viaje en minutos (24 horas)
                false, // no empezar acumulando desde cero
                "Time"
        );

        RoutingDimension timeDimension = routing.getMutableDimension("Time");

        // Añadir restricciones de pickups y deliveries
        for (int i = 0; i < numOrders; i++) {
            Order order = validOrders.get(i);
            int pickupNode = numLocations + 2 * i;
            int deliveryNode = pickupNode + 1;

            long pickupIndex = manager.nodeToIndex(pickupNode);
            long deliveryIndex = manager.nodeToIndex(deliveryNode);

            // Vincular pickup y delivery
            routing.addPickupAndDelivery(pickupIndex, deliveryIndex);

            // Asegurar que ambos sean atendidos por el mismo vehículo
            routing.solver().addConstraint(
                    routing.solver().makeEquality(routing.vehicleVar(pickupIndex), routing.vehicleVar(deliveryIndex)));

            // Asegurar que el pickup ocurre antes que el delivery
            routing.solver().addConstraint(routing.solver().makeLessOrEqual(
                    timeDimension.cumulVar(pickupIndex), timeDimension.cumulVar(deliveryIndex)));

            // Opcional: Añadir ventanas de tiempo específicas para pickup y delivery
            timeDimension.cumulVar(pickupIndex).setRange(order.getOrderTime(), order.getDueTime());
            timeDimension.cumulVar(deliveryIndex).setRange(order.getOrderTime(), order.getDueTime());
        }

        // Añadir la dimensión de capacidad
        int capacityCallbackIndex = routing.registerUnaryTransitCallback((long fromIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            // Determinar si el nodo es pickup o delivery
            String ubigeo = ubigeoList.get(fromNode);
            if (ubigeo.contains("_pickup_")) {
                // Es un pickup
                int orderId = extractOrderId(ubigeo);
                Order order = getOrderById(orderId);
                return order != null ? order.getQuantity() : 0;
            } else if (ubigeo.contains("_delivery_")) {
                // Es un delivery
                int orderId = extractOrderId(ubigeo);
                Order order = getOrderById(orderId);
                return order != null ? -order.getQuantity() : 0;
            }
            return 0;
        });

        routing.addDimensionWithVehicleCapacity(
                capacityCallbackIndex,
                0, // no slack
                getVehicleCapacities(availableVehicles), // vehicle maximum capacities
                true, // start cumul to zero
                "Capacity"
        );
    }

    private long calculateTravelTime(int fromNode, int toNode) {
        String fromUbigeo = ubigeoList.get(fromNode);
        String toUbigeo = ubigeoList.get(toNode);

        // Buscar una conexión directa
        for (Edge edge : edges) {
            if (edge.getOriginUbigeo().equals(fromUbigeo) && edge.getDestinationUbigeo().equals(toUbigeo)) {
                return (long) (edge.getTravelTime() * 60); // Convertir a minutos
            }
        }

        // Si no hay conexión directa, devolver un valor alto pero no infinito
        return 1000000; // Aproximadamente 16 horas
    }

    private List<Vehicle> getAvailableVehicles() {
        List<Vehicle> availableVehicles = new ArrayList<>();
        for (VehicleState state : vehicleStates) {
            if (!state.isInTransit() && state.getAvailableTime() <= simulationCurrentTime) {
                availableVehicles.add(state.getVehicle());
                System.out.println("Vehículo disponible: " + state.getVehicle().getCode() + " en " + state.getCurrentUbigeo());
            } else {
                System.out.println("Vehículo NO disponible: " + state.getVehicle().getCode() +
                        ". En tránsito: " + state.isInTransit() +
                        ". Disponible en: " + formatMinutes(state.getAvailableTime()));
            }
        }
        return availableVehicles;
    }

    private long[] getVehicleCapacities(List<Vehicle> availableVehicles) {
        return availableVehicles.stream()
                .mapToLong(Vehicle::getCapacity)
                .toArray();
    }

    private void printLoadedEdges() {
        System.out.println("Edges cargados:");
        for (Edge edge : edges) {
            System.out.println(edge.getOriginUbigeo() + " => " + edge.getDestinationUbigeo());
        }
        System.out.println();
    }

    private void updateVehicleStatesWithNewRoutes(List<VehicleRoute> vehicleRoutes) {
        for (VehicleRoute route : vehicleRoutes) {
            VehicleState state = getVehicleState(route.getVehicleCode());
            List<RouteStep> steps = route.getSteps();
            if (!steps.isEmpty()) {
                RouteStep nextStep = steps.get(0);
                state.setCurrentUbigeo(nextStep.getUbigeo());
                state.setAvailableTime(nextStep.getArrivalTime());
                state.setInTransit(true);
                System.out.println("Vehículo " + state.getVehicle().getCode() +
                        " en ruta hacia " + nextStep.getUbigeo() +
                        ", llegada estimada: " + formatMinutes(nextStep.getArrivalTime()));
            }
        }
    }

    private void triggerVehicleRelocation() {
        // Implementar lógica de reubicación de vehículos según las necesidades
        // Este método debe ser definido según los requerimientos específicos
        System.out.println("Función de reubicación de vehículos no implementada.");
    }

    private void updateOrderStates(List<Order> assignedOrders) {
        for (Order order : assignedOrders) {
            OrderState state = orderStates.get(order.getId());
            state.setAssigned(true);
        }
    }

    private VehicleState getVehicleState(String vehicleCode) {
        for (VehicleState state : vehicleStates) {
            if (state.getVehicle().getCode().equals(vehicleCode)) {
                return state;
            }
        }
        return null;
    }

    private int extractOrderId(String ubigeo) {
        // Extraer el ID del pedido del string de ubigeo
        // Ejemplo: "150101_pickup_1" -> 1
        String[] parts = ubigeo.split("_");
        if (parts.length == 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                System.out.println("Error al extraer el ID del pedido de: " + ubigeo);
            }
        }
        return -1;
    }

    private Order getOrderById(int orderId) {
        for (Order order : allOrders) {
            if (order.getId() == orderId) {
                return order;
            }
        }
        return null;
    }

    private List<VehicleRoute> printSolution(RoutingModel routing, RoutingIndexManager manager, Assignment solution, List<Order> validOrders, List<Vehicle> availableVehicles) {
        RoutingDimension timeDimension = routing.getMutableDimension("Time");

        // Estructura para almacenar los movimientos de los vehículos
        List<VehicleRoute> vehicleRoutes = new ArrayList<>();

        // Obtener el número de vehículos
        int numVehicles = manager.getNumberOfVehicles();

        for (int vehicleId = 0; vehicleId < numVehicles; ++vehicleId) {
            long index = routing.start(vehicleId);
            Vehicle vehicle = availableVehicles.get(vehicleId);
            StringBuilder routeStr = new StringBuilder();

            List<RouteStep> steps = new ArrayList<>();

            while (!routing.isEnd(index)) {
                int nodeIndex = manager.indexToNode(index);
                String ubigeo = ubigeoList.get(nodeIndex);

                IntVar timeVar = timeDimension.cumulVar(index);
                long arrivalTime = solution.min(timeVar);
                long departureTime = solution.max(timeVar); // Suponiendo sin tiempo de servicio

                // Convertir los tiempos a formato legible
                String formattedArrival = formatMinutes(arrivalTime);
                String formattedDeparture = formatMinutes(departureTime);

                Location currentLocation = locations.get(ubigeo.split("_")[0]); // Obtener ubicación base
                steps.add(new RouteStep(ubigeo, currentLocation.getLatitude(), currentLocation.getLongitude(), arrivalTime, departureTime));

                routeStr.append(ubigeo).append(" [Llegada: ").append(formattedArrival)
                        .append(", Salida: ").append(formattedDeparture).append("] -> ");

                index = solution.value(routing.nextVar(index));
            }

            // Nodo final (almacén)
            int endNodeIndex = manager.indexToNode(index);
            String endUbigeo = ubigeoList.get(endNodeIndex);
            long endTime = solution.min(timeDimension.cumulVar(index));

            String formattedEndTime = formatMinutes(endTime);
            routeStr.append(endUbigeo).append(" [Llegada: ").append(formattedEndTime).append("]");

            // Obtener la ubicación final
            Location endLocation = locations.get(endUbigeo.split("_")[0]); // Obtener ubicación base

            // Agregar el último paso
            RouteStep finalStep = new RouteStep(endUbigeo, endLocation.getLatitude(), endLocation.getLongitude(), endTime, endTime);
            steps.add(finalStep);

            // Imprimir la ruta del vehículo
            System.out.println("Ruta para el vehículo " + vehicle.getCode() + ":");
            System.out.println(routeStr.toString());
            System.out.println();

            // Agregar la ruta del vehículo a la lista general
            vehicleRoutes.add(new VehicleRoute(vehicle.getCode(), steps));
        }

        // Exportar las rutas a un archivo JSON
        if (!vehicleRoutes.isEmpty()) {
            exportRoutesToJson(vehicleRoutes);
        }

        return vehicleRoutes;
    }

    private void createUbigeoListAndMapping(List<Order> activeOrders) {
        ubigeoList = new ArrayList<>();
        ubigeoToNodeIndex = new HashMap<>();

        System.out.println("Creando lista de ubigeos y mapeo:");

        // Agregar todas las ubicaciones de los edges
        for (Edge edge : edges) {
            addUbigeoToList(edge.getOriginUbigeo());
            addUbigeoToList(edge.getDestinationUbigeo());
        }

        // Agregar las ubicaciones de los pedidos activos (pickup y delivery)
        for (Order order : activeOrders) {
            String pickupKey = order.getOriginUbigeo() + "_pickup_" + order.getId();
            String deliveryKey = order.getDestinationUbigeo() + "_delivery_" + order.getId();
            addUbigeoToList(pickupKey);
            addUbigeoToList(deliveryKey);
        }

        // Agregar las ubicaciones de los vehículos (si no se agregaron anteriormente)
        for (Vehicle vehicle : vehicles) {
            addUbigeoToList(vehicle.getCurrentLocationUbigeo());
            addUbigeoToList(vehicle.getHomeUbigeo());
        }

        System.out.println("Ubicaciones en ubigeoList:");
        for (int i = 0; i < ubigeoList.size(); i++) {
            System.out.println(i + ": " + ubigeoList.get(i));
        }
        System.out.println();
    }

    private void addUbigeoToList(String ubigeo) {
        if (!ubigeoToNodeIndex.containsKey(ubigeo)) {
            ubigeoToNodeIndex.put(ubigeo, ubigeoList.size());
            ubigeoList.add(ubigeo);
            System.out.println("Agregado ubigeo: " + ubigeo + " con índice: " + (ubigeoList.size() - 1));
        }
    }

    private int[][] createTimeMatrix(int numLocations) {
        int[][] timeMatrix = new int[numLocations][numLocations];

        // Inicializar la matriz con valores altos (no conexión)
        for (int i = 0; i < numLocations; i++) {
            Arrays.fill(timeMatrix[i], Integer.MAX_VALUE / 2);
            timeMatrix[i][i] = 0; // Tiempo de viaje de un nodo a sí mismo es 0
        }

        System.out.println("Creando matriz de tiempos para " + numLocations + " ubicaciones");

        if (edges.isEmpty()) {
            System.out.println("¡Advertencia! No hay edges cargados. La matriz de tiempos estará vacía.");
            return timeMatrix;
        }

        // Llenar la matriz con los tiempos de viaje entre ubicaciones conectadas
        for (Edge edge : edges) {
            String originUbigeo = edge.getOriginUbigeo();
            String destinationUbigeo = edge.getDestinationUbigeo();

            // Claves únicas para pickup y delivery
            String pickupKey = originUbigeo + "_pickup_" + edge.getOrderId(); // Asegúrate de que Edge tenga orderId
            String deliveryKey = destinationUbigeo + "_delivery_" + edge.getOrderId();

            // Obtener índices de origen y destino
            Integer fromIndex = ubigeoToNodeIndex.get(pickupKey);
            Integer toIndex = ubigeoToNodeIndex.get(deliveryKey);

            if (fromIndex == null || toIndex == null) {
                System.out.println("Error: No se encontró la ubicación de pickup o delivery para el edge: " + originUbigeo + " -> " + destinationUbigeo);
                continue;
            }

            // Definir tiempo de viaje
            int travelTime = 60; // Asumimos 60 minutos por cada edge si no se proporciona un tiempo específico

            // Actualizar la matriz para la conexión directa
            timeMatrix[fromIndex][toIndex] = travelTime;
            timeMatrix[toIndex][fromIndex] = travelTime; // Asumiendo que es bidireccional

            System.out.println("Procesando borde: " + pickupKey + " => " + deliveryKey);
            System.out.println("Índices: " + fromIndex + " => " + toIndex);
            System.out.println("Tiempo de viaje establecido: " + travelTime + " minutos");
        }

        // Ejecutar el Algoritmo de Floyd-Warshall para calcular las rutas más cortas
        for (int k = 0; k < numLocations; k++) {
            for (int i = 0; i < numLocations; i++) {
                for (int j = 0; j < numLocations; j++) {
                    if (timeMatrix[i][k] + timeMatrix[k][j] < timeMatrix[i][j]) {
                        timeMatrix[i][j] = timeMatrix[i][k] + timeMatrix[k][j];
                    }
                }
            }
        }

        // Imprimir la matriz de tiempos después de Floyd-Warshall
        System.out.println("Matriz de tiempos después de Floyd-Warshall:");
        for (int i = 0; i < numLocations; i++) {
            for (int j = 0; j < numLocations; j++) {
                if (timeMatrix[i][j] >= Integer.MAX_VALUE / 2) {
                    System.out.print("INF ");
                } else {
                    System.out.print(timeMatrix[i][j] + " ");
                }
            }
            System.out.println();
        }

        return timeMatrix;
    }

    private boolean isDeliveryOffice(String ubigeo, List<Order> activeOrders) {
        // Las oficinas de entrega son los destinos de los pedidos activos
        for (Order order : activeOrders) {
            if (order.getDestinationUbigeo().equals(ubigeo)) {
                return true;
            }
        }
        return false;
    }

    private Order getOrderByDestinationUbigeo(String ubigeo, List<Order> activeOrders) {
        for (Order order : activeOrders) {
            if (order.getDestinationUbigeo().equals(ubigeo)) {
                return order;
            }
        }
        return null;
    }

    private void exportRoutesToJson(List<VehicleRoute> vehicleRoutes) {
        // Usamos Gson para convertir los objetos a JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Convertir las rutas a JSON
        String json = gson.toJson(vehicleRoutes);

        // Guardar el JSON en un archivo
        try (FileWriter writer = new FileWriter("vehicle_routes_" + simulationCurrentTime + ".json")) {
            writer.write(json);
            System.out.println("Las rutas de los vehículos se han exportado a 'vehicle_routes_" + simulationCurrentTime + ".json'.");
        } catch (IOException e) {
            System.out.println("Error al exportar las rutas a JSON: " + e.getMessage());
        }
    }

    private void preprocessEdges() {
        Iterator<Edge> iterator = edges.iterator();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            for (Blockage blockage : blockages) {
                if (isSameEdge(blockage, edge.getOriginUbigeo(), edge.getDestinationUbigeo())) {
                    if (intervalsOverlap(simulationStartTime, simulationEndTime, blockage.getStartTime(), blockage.getEndTime())) {
                        // Eliminar el arco de la lista
                        System.out.println("Eliminando arco bloqueado: " + edge.getOriginUbigeo() + " -> " + edge.getDestinationUbigeo());
                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

    private boolean isSameEdge(Blockage blockage, String originUbigeo, String destinationUbigeo) {
        return (blockage.getOriginUbigeo().equals(originUbigeo) && blockage.getDestinationUbigeo().equals(destinationUbigeo))
                || (blockage.getOriginUbigeo().equals(destinationUbigeo) && blockage.getDestinationUbigeo().equals(originUbigeo));
    }

    private boolean intervalsOverlap(long start1, long end1, long start2, long end2) {
        return start1 < end2 && start2 < end1;
    }
}
*/
