package com.odiparpack.simulation.route;

import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import com.odiparpack.DataLoader;
import com.odiparpack.DataModel;
import com.odiparpack.models.*;
import com.odiparpack.simulation.blockage.BlockageManager;
import com.odiparpack.utils.Utils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * La clase RouteManager se encarga de calcular y asignar rutas a los vehículos.
 */
public class RouteManager {
    private static final Logger logger = Logger.getLogger(RouteManager.class.getName());

    private final RouteCache routeCache;
    private final Map<String, Integer> locationIndices;
    private final List<String> locationNames;
    private final List<String> locationUbigeos;
    private final BlockageManager blockageManager;

    /**
     * Constructor de RouteManager.
     *
     * @param routeCache      Caché de rutas calculadas.
     * @param locationIndices Mapa de ubigeos a índices en la matriz de tiempos.
     * @param locationNames   Lista de nombres de ubicaciones.
     * @param locationUbigeos Lista de ubigeos de ubicaciones.
     * @param blockageManager Referencia a BlockageManager para obtener bloqueos activos.
     */
    public RouteManager(RouteCache routeCache, Map<String, Integer> locationIndices,
                        List<String> locationNames, List<String> locationUbigeos,
                        BlockageManager blockageManager) {
        this.routeCache = routeCache;
        this.locationIndices = locationIndices;
        this.locationNames = locationNames;
        this.locationUbigeos = locationUbigeos;
        this.blockageManager = blockageManager;
    }

    /**
     * Calcula rutas hacia almacenes para una lista de vehículos.
     *
     * @param vehicles          Lista de vehículos a los que se les asignarán rutas.
     * @param warehousesUbigeos Lista de ubigeos de los almacenes principales.
     * @return Mapa de vehículos a sus respectivas rutas.
     */
    public Map<Vehicle, List<RouteSegment>> calculateRoutesToWarehouses(List<Vehicle> vehicles,
                                                                        List<String> warehousesUbigeos, long[][] timeMatrix) {
        Map<Vehicle, List<RouteSegment>> vehicleRoutes = new HashMap<>();
        Set<RouteRequest> routesToCalculate = new HashSet<>();

        for (Vehicle vehicle : vehicles) {
            String currentLocation = vehicle.getCurrentLocationUbigeo();
            Map<String, Long> routeTimes = new HashMap<>();

            for (String warehouseUbigeo : warehousesUbigeos) {
                if (!warehouseUbigeo.equals(currentLocation)) {
                    List<RouteSegment> cachedRoute = routeCache.getRoute(currentLocation, warehouseUbigeo, blockageManager.getActiveBlockages());
                    if (cachedRoute != null) {
                        long routeTime = calculateRouteTime(cachedRoute);
                        routeTimes.put(warehouseUbigeo, routeTime);
                    } else {
                        routesToCalculate.add(new RouteRequest(currentLocation, warehouseUbigeo));
                    }
                }
            }

            if (!routeTimes.isEmpty()) {
                String bestDestination = findBestDestination(routeTimes);
                List<RouteSegment> route = routeCache.getRoute(currentLocation, bestDestination, blockageManager.getActiveBlockages());
                vehicleRoutes.put(vehicle, route);
            }
        }

        if (!routesToCalculate.isEmpty()) {
            Map<RouteRequest, List<RouteSegment>> calculatedRoutes = batchCalculateRoutes(routesToCalculate, timeMatrix);
            for (Map.Entry<RouteRequest, List<RouteSegment>> entry : calculatedRoutes.entrySet()) {
                RouteRequest request = entry.getKey();
                List<RouteSegment> route = entry.getValue();
                Vehicle vehicle = vehicles.stream()
                        .filter(v -> v.getCurrentLocationUbigeo().equals(request.start))
                        .findFirst()
                        .orElse(null);
                if (vehicle != null) {
                    vehicleRoutes.put(vehicle, route);
                }
            }
        }

        return vehicleRoutes;
    }

    /**
     * Calcula rutas en batch utilizando Google OR-Tools.
     *
     * @param routesToCalculate Conjunto de solicitudes de rutas a calcular.
     * @return Mapa de solicitudes de rutas a sus respectivas rutas calculadas.
     */
    private Map<RouteRequest, List<RouteSegment>> batchCalculateRoutes(Set<RouteRequest> routesToCalculate,
                                                                       long[][] timeMatrix) {
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        for (RouteRequest request : routesToCalculate) {
            Integer startIndex = locationIndices.get(request.start);
            Integer endIndex = locationIndices.get(request.end);
            if (startIndex != null && endIndex != null) {
                starts.add(startIndex);
                ends.add(endIndex);
            } else {
                logger.warning(String.format("Ubigeo de inicio o fin no encontrado en locationIndices: %s -> %s",
                        request.start, request.end));
            }
        }

        if (starts.isEmpty() || ends.isEmpty()) {
            logger.warning("No hay rutas válidas para calcular.");
            return Collections.emptyMap();
        }

        DataModel data = new DataModel(timeMatrix, blockageManager.getActiveBlockages(),
                starts.stream().mapToInt(Integer::intValue).toArray(),
                ends.stream().mapToInt(Integer::intValue).toArray(),
                locationNames, locationUbigeos);

        List<List<RouteSegment>> calculatedRoutes = calculateRoutes(data);

        if (calculatedRoutes.isEmpty()) {
            logger.warning("No se pudieron calcular rutas.");
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

    /**
     * Calcula rutas utilizando Google OR-Tools.
     *
     * @param data Modelo de datos para la optimización de rutas.
     * @return Lista de rutas calculadas, cada una representada como una lista de RouteSegment.
     */
    public List<List<RouteSegment>> calculateRoutes(DataModel data) {
        RoutingIndexManager manager = createRoutingIndexManager(data);
        RoutingModel routing = createRoutingModel(manager, data);
        RoutingSearchParameters searchParameters = createSearchParameters();

        logger.info("Iniciando la resolución del modelo de rutas.");
        Assignment solution = routing.solveWithParameters(searchParameters);
        logger.info("Solución de rutas obtenida.");

        if (solution != null) {
            return extractCalculatedRoutes(manager, data, routing, solution);
        } else {
            logger.warning("No se encontró solución para las rutas.");
            return new ArrayList<>();
        }
    }

    /**
     * Calcula una ruta específica para una asignación de vehículo y orden.
     *
     * @param vehicle El vehículo a asignar la ruta.
     * @param order   La orden asociada a la ruta.
     * @return Lista de RouteSegment representando la ruta calculada.
     */
    public List<RouteSegment> calculateRouteForAssignment(Vehicle vehicle, Order order, long[][] timeMatrix) {
        String originUbigeo = vehicle.getCurrentLocationUbigeo();
        String destinationUbigeo = order.getDestinationUbigeo();

        if (originUbigeo.equals(destinationUbigeo)) {
            logger.info(String.format("El vehículo %s ya está en el destino de la orden %d.", vehicle.getCode(), order.getId()));
            return Collections.emptyList();
        }

        List<RouteSegment> cachedRoute = routeCache.getRoute(originUbigeo, destinationUbigeo, blockageManager.getActiveBlockages());
        if (cachedRoute != null) {
            return cachedRoute;
        } else {
            RouteRequest request = new RouteRequest(originUbigeo, destinationUbigeo);
            Map<RouteRequest, List<RouteSegment>> calculatedRoutes = batchCalculateRoutes(Collections.singleton(request), timeMatrix);
            return calculatedRoutes.getOrDefault(request, Collections.emptyList());
        }
    }

    /**
     * Crea un RoutingIndexManager para Google OR-Tools.
     *
     * @param data Modelo de datos.
     * @return Instancia de RoutingIndexManager.
     */
    private RoutingIndexManager createRoutingIndexManager(DataModel data) {
        return new RoutingIndexManager(
                data.timeMatrix.length,
                data.vehicleNumber,
                data.starts,
                data.ends);
    }

    /**
     * Crea un RoutingModel para Google OR-Tools.
     *
     * @param manager RoutingIndexManager.
     * @param data    Modelo de datos.
     * @return Instancia de RoutingModel.
     */
    private RoutingModel createRoutingModel(RoutingIndexManager manager, DataModel data) {
        RoutingModel routing = new RoutingModel(manager);

        final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return data.timeMatrix[fromNode][toNode];
        });

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);
        routing.addDimension(transitCallbackIndex, 0, Integer.MAX_VALUE, true, "Time");

        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        timeDimension.setGlobalSpanCostCoefficient(100);

        return routing;
    }

    /**
     * Crea los parámetros de búsqueda para Google OR-Tools.
     *
     * @return Instancia de RoutingSearchParameters.
     */
    private RoutingSearchParameters createSearchParameters() {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(10).build())
                .setLogSearch(true)  // Habilitar "verbose logging"
                .build();
        return searchParameters;
    }

    /**
     * Extrae las rutas calculadas desde la solución de Google OR-Tools.
     *
     * @param manager  RoutingIndexManager.
     * @param data     Modelo de datos.
     * @param routing  RoutingModel.
     * @param solution Solución de la optimización.
     * @return Lista de rutas calculadas.
     */
    private List<List<RouteSegment>> extractCalculatedRoutes(RoutingIndexManager manager, DataModel data, RoutingModel routing, Assignment solution) {
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
                double distance = Utils.calculateDistanceFromUbigeos(fromUbigeo, toUbigeo);

                route.add(new RouteSegment(fromName + " to " + toName, fromUbigeo, toUbigeo, distance, durationMinutes));

                index = nextIndex;
            }

            calculatedRoutes.add(route);
            logger.info("Ruta calculada para el vehículo " + i + " con " + route.size() + " segmentos.");
        }
        return calculatedRoutes;
    }

    /**
     * Calcula el tiempo total de una ruta.
     *
     * @param route Lista de segmentos de la ruta.
     * @return Tiempo total en minutos.
     */
    private long calculateRouteTime(List<RouteSegment> route) {
        return route.stream().mapToLong(RouteSegment::getDurationMinutes).sum();
    }

    /**
     * Encuentra el mejor destino basado en el tiempo de ruta más corto.
     *
     * @param routeTimes Mapa de destinos a tiempos de ruta.
     * @return Ubigeo del mejor destino.
     */
    private String findBestDestination(Map<String, Long> routeTimes) {
        return routeTimes.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Clase interna para representar una solicitud de ruta.
     */
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
            if (!(o instanceof RouteRequest)) return false;
            RouteRequest that = (RouteRequest) o;
            return Objects.equals(start, that.start) && Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }
}
