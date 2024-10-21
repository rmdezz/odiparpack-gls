package com.odiparpack;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.odiparpack.models.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.protobuf.Duration;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import static com.odiparpack.Utils.*;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class Main {
    public static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final int SIMULATION_DAYS = 7;
    private static final int SIMULATION_SPEED = 10; // 1 minuto de simulación = 1 segundo de tiempo real
    private static final int PLANNING_INTERVAL_MINUTES = 15;
    private static final int ROUTE_CACHE_CAPACITY = 1000;
    private static final int TIME_ADVANCEMENT_INTERVAL_MINUTES = 5; // New variable for time advancement interval

    private static RouteCache routeCache;
    public static Map<String, Integer> locationIndices;
    public static long[][] timeMatrix;
    public static List<String> locationNames;
    public static List<String> locationUbigeos;
    public static Map<String, Location> locations;

    // Variable global para almacenar el tiempo total de entrega de todos los pedidos
    private static long totalDeliveryTime = 0;

    static class Metrics {
        private int iteracion;
        private long tiempoEjecucionMs;
        private long tiempoEntregaMin;
        private double consumoMemoriaMb;
        private double consumoCpuPorcentaje;

        public Metrics(int iteracion, long tiempoEjecucionMs, long tiempoEntregaMin, double consumoMemoriaMb, double consumoCpuPorcentaje) {
            this.iteracion = iteracion;
            this.tiempoEjecucionMs = tiempoEjecucionMs;
            this.tiempoEntregaMin = tiempoEntregaMin;
            this.consumoMemoriaMb = consumoMemoriaMb;
            this.consumoCpuPorcentaje = consumoCpuPorcentaje;
        }

        @Override
        public String toString() {
            return String.format("%d\t%d\t%d\t%.2f\t%.2f",
                    iteracion,
                    tiempoEjecucionMs,
                    tiempoEntregaMin,
                    consumoMemoriaMb,
                    consumoCpuPorcentaje);
        }
    }


    public static void main(String[] args) throws IOException {
        Loader.loadNativeLibraries();

        DataLoader dataLoader = new DataLoader();

        // Cargar datos
        locations = dataLoader.loadLocations("src/main/resources/locations.txt");
        List<Edge> edges = dataLoader.loadEdges("src/main/resources/edges.txt", locations);
        List<Vehicle> vehicles = dataLoader.loadVehicles("src/main/resources/vehicles.txt");
        List<Order> orders = dataLoader.loadOrders("src/main/resources/orders.txt", locations);
        List<Blockage> blockages = dataLoader.loadBlockages("src/main/resources/blockages.txt");
        List<Maintenance> maintenanceSchedule = dataLoader.loadMaintenanceSchedule("src/main/resources/maintenance.txt");

        routeCache = new RouteCache(ROUTE_CACHE_CAPACITY);

        List<Location> locationList = new ArrayList<>(locations.values());
        locationIndices = new HashMap<>();
        for (int i = 0; i < locationList.size(); i++) {
            locationIndices.put(locationList.get(i).getUbigeo(), i);
        }

        timeMatrix = dataLoader.createTimeMatrix(locationList, edges);

        locationNames = new ArrayList<>();
        locationUbigeos = new ArrayList<>();
        for (Location loc : locationList) {
            locationNames.add(loc.getProvince());
            locationUbigeos.add(loc.getUbigeo());
        }

        List<Metrics> allMetrics = new ArrayList<>();
        int subset = 40;

        // Buscar archivos con el formato execution_metrics_subset_x_iter_y.txt y resource_usage_subset_x_iter_y.txt
        List<Path> executionFiles = findFilesByPattern("execution_metrics_subset_" + subset + "_iter_\\d+\\.txt");
        List<Path> resourceFiles = findFilesByPattern("resource_usage_subset_" + subset + "_iter_\\d+\\.txt");

        System.out.println("Archivos de ejecución encontrados: " + executionFiles.size());
        System.out.println("Archivos de recursos encontrados: " + resourceFiles.size());

        // Mapear archivos por número de iteración
        Map<Integer, Path> executionMap = mapFilesByIteration(executionFiles);
        Map<Integer, Path> resourceMap = mapFilesByIteration(resourceFiles);

        // Obtener el conjunto de iteraciones presentes en ambos mapas
        Set<Integer> iteraciones = new TreeSet<>(executionMap.keySet());
        iteraciones.retainAll(resourceMap.keySet());

        System.out.println("Número de iteraciones emparejadas: " + iteraciones.size());

        // Procesar cada iteración emparejada
        for (Integer iter : iteraciones) {
            Path executionFile = executionMap.get(iter);
            Path resourceFile = resourceMap.get(iter);

            Metrics metrics = extractMetrics(executionFile, resourceFile);
            if (metrics != null) {
                allMetrics.add(metrics);
            }
        }

        // Mostrar los resultados en formato tabla
        System.out.println("# Iteracion\tTiempo de ejecucion (ms)\tTiempo total de entrega (min)\tConsumo de memoria promedio (MB)\tConsumo de CPU promedio (%)");
        for (Metrics metrics : allMetrics) {
            System.out.println(metrics);
        }

        // iter: 6 problema con 30 ped
        // iter: 14 se cae tmb con 30

        // iter: 10, 23 se cae con ped40
        //executeForEachSubset(orders, vehicles, 30, 40, 24);

        /*LocalDateTime currentTime = LocalDateTime.now();
        List<Order> availableOrders = orders; // tomamos todos los pedidos ignorando el tiempo en el que apareceran
        if (!availableOrders.isEmpty()) {
            logger.info("Órdenes disponibles: " + availableOrders.size());
            List<VehicleAssignment> assignments = assignOrdersToVehicles(availableOrders, vehicles, currentTime);

            Map<String, VehicleAssignment> uniqueDestinationMap = new HashMap<>();
            for (VehicleAssignment assignment : assignments) {
                String destination = assignment.getOrder().getDestinationUbigeo();
                uniqueDestinationMap.putIfAbsent(destination, assignment);
            }

            // Convertir el mapa filtrado a una lista
            List<VehicleAssignment> filteredAssignments = new ArrayList<>(uniqueDestinationMap.values());

            // Crea una instancia del monitor
            ResourceMonitor monitor = new ResourceMonitor();
            long durationInMillis = 0;

            try {
                // Inicia el monitoreo
                monitor.startMonitoring();

                // Tomar el tiempo inicial antes de iniciar la resolución
                long startTime = System.nanoTime();

                // Resolver el conjunto completo de asignaciones
                DataModel data = new DataModel(timeMatrix, new ArrayList<>(), filteredAssignments, locationIndices, locationNames, locationUbigeos);
                RoutingIndexManager manager = createRoutingIndexManager(data, data.starts, data.ends);
                RoutingModel routing = createRoutingModel(manager, data);
                RoutingSearchParameters searchParameters = createSearchParameters();

                logger.info("Iniciando la resolución del modelo de rutas para el conjunto completo.");
                Assignment solution = routing.solveWithParameters(searchParameters);

                if (solution != null) {
                    logger.info("Solución encontrada para el conjunto completo.");
                    printSolution(data, routing, manager, solution);
                    logger.info("Rutas calculadas.");
                } else {
                    logger.info("No se encontró solución para el conjunto completo. Iniciando la división del conjunto...");

                    // Definir las estrategias a intentar en orden
                    List<FirstSolutionStrategy.Value> strategies = Arrays.asList(
                            FirstSolutionStrategy.Value.CHRISTOFIDES,
                            FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC
                    );
                    List<SolutionData> solutions = Collections.synchronizedList(new ArrayList<>());
                    divideAndSolve(assignments, strategies, solutions); // Llamada recursiva si falla la resolución completa

                    // Después de que todas las computaciones hayan finalizado, imprimimos las soluciones
                    for (SolutionData solutionData : solutions) {
                        logger.info("SOLUCION ASDASD");
                        printSolutionData(solutionData);
                    }
                }

                // Tomar el tiempo final después de completar todo
                long endTime = System.nanoTime();

                // Calcular el tiempo transcurrido en milisegundos
                durationInMillis = (endTime - startTime) / 1_000_000;

                logger.info("Tiempo transcurrido en resolver las rutas: " + durationInMillis + " ms");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Detiene el monitoreo
                monitor.stopMonitoring();
            }

            // Después de que el monitoreo ha terminado, genera las gráficas
            generateGraphs(monitor);
            // Imprimir el tiempo total de entrega acumulado
            logger.info("Tiempo total de entrega acumulado: " + totalDeliveryTime + " minutos.");

            // Guardar las métricas de ejecución en un archivo txt
            saveExecutionMetrics(totalDeliveryTime, durationInMillis);
        }*/



           /* if (!assignments.isEmpty()) {
                DataModel data = new DataModel(timeMatrix, new ArrayList<>(), filteredAssignments, locationIndices, locationNames, locationUbigeos);

                logger.info("Calculando rutas para asignaciones...");
                RoutingIndexManager manager = createRoutingIndexManager(data, data.starts, data.ends);
                RoutingModel routing = createRoutingModel(manager, data);
                RoutingSearchParameters searchParameters = createSearchParameters();

                logger.info("Iniciando la resolución del modelo de rutas para rutas faltantes.");
                Assignment solution = routing.solveWithParameters(searchParameters);
                logger.info("Solución de rutas obtenida para rutas faltantes.");
                if (solution != null) {
                    printSolution(data, routing, manager, solution);
                    logger.info("Rutas calculadas.");
                } else {
                    logger.info("No se encontró solución..");
                }
            }
        } else {
            logger.info("No hay órdenes disponibles en este momento.");
        }*/

        /*// Iniciar simulación
        runSimulation(timeMatrix, orders, vehicles, locationIndices, locationNames, locationUbigeos, locations, routeCache,
                blockages, maintenanceSchedule);*/

        /*// Calcular la ruta de ida
        logger.info("Calculando ruta de ida (Almacén -> Destino Final):");
        calculateRoute(data, data.starts, data.ends);*/

        /*List<Order> orders = dataLoader.loadOrders("src/main/resources/orders.txt", locations);
        List<Blockage> blockages = dataLoader.loadBlockages("src/main/resources/blockages.txt");
        List<Maintenance> maintenances = dataLoader.loadMaintenanceSchedule("src/main/resources/maintenance.txt");

        // Crear instancia del solver y resolver
        OdiparPackSolver solver = new OdiparPackSolver(locations, orders, vehicles, edges, blockages, maintenances);
        solver.runSimulation();*/
    }

    // Función para encontrar archivos por patrón
    private static List<Path> findFilesByPattern(String pattern) throws IOException {
        Path dir = Paths.get("."); // Directorio actual
        System.out.println("Buscando en el directorio: " + dir.toAbsolutePath().toString()); // Imprimir el directorio de búsqueda
        Pattern regex = Pattern.compile(pattern);

        // Buscar archivos en el directorio especificado que coincidan con el patrón
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(file -> !Files.isDirectory(file) && regex.matcher(file.getFileName().toString()).matches())
                    .collect(Collectors.toList());
        }
    }

    // Función para mapear archivos por número de iteración
    private static Map<Integer, Path> mapFilesByIteration(List<Path> files) {
        Map<Integer, Path> fileMap = new HashMap<>();
        for (Path file : files) {
            int iter = extractIterationFromFilename(file.getFileName().toString());
            if (iter != -1) {
                fileMap.put(iter, file);
            }
        }
        return fileMap;
    }

    // Función para extraer el número de iteración del nombre de archivo
    private static int extractIterationFromFilename(String filename) {
        Pattern pattern = Pattern.compile("iter_(\\d+)(?=\\D|$)");
        Matcher matcher = pattern.matcher(filename);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    // Función para extraer métricas de los archivos
    private static Metrics extractMetrics(Path executionFile, Path resourceFile) {
        int iteracion = extractIterationFromFilename(executionFile.getFileName().toString());

        long tiempoEntregaMin = 0;
        long tiempoEjecucionMs = 0;
        double consumoMemoriaMb = 0;
        double consumoCpuPorcentaje = 0;

        try {
            // Leer el archivo execution_metrics
            List<String> executionLines = Files.readAllLines(executionFile);
            for (String line : executionLines) {
                if (line.contains("Tiempo total de entrega acumulado:")) {
                    tiempoEntregaMin = Long.parseLong(line.replaceAll("[^0-9]", "").trim());
                } else if (line.contains("Tiempo transcurrido en resolver las rutas:")) {
                    tiempoEjecucionMs = Long.parseLong(line.replaceAll("[^0-9]", "").trim());
                }
            }

            // Leer el archivo resource_usage
            List<String> resourceLines = Files.readAllLines(resourceFile);
            Pattern cpuPattern = Pattern.compile("Media de Uso de CPU:\\s+([0-9.]+)");
            Pattern memoryPattern = Pattern.compile("Media de Uso de Memoria:\\s+([0-9.]+)");

            for (String line : resourceLines) {
                Matcher cpuMatcher = cpuPattern.matcher(line);
                Matcher memoryMatcher = memoryPattern.matcher(line);

                if (cpuMatcher.find()) {
                    consumoCpuPorcentaje = Double.parseDouble(cpuMatcher.group(1)); // Extraer el valor numérico de la CPU
                }
                if (memoryMatcher.find()) {
                    consumoMemoriaMb = Double.parseDouble(memoryMatcher.group(1)); // Extraer el valor numérico de la memoria
                }
            }

            return new Metrics(iteracion, tiempoEjecucionMs, tiempoEntregaMin, consumoMemoriaMb, consumoCpuPorcentaje);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Ejecuta el procesamiento de pedidos en subconjuntos, permitiendo iniciar desde una iteración específica
     * y registrando los rangos de pedidos procesados.
     *
     * @param orders        Lista completa de pedidos a procesar.
     * @param vehicles      Lista de vehículos disponibles para asignación.
     * @param iterations    Número total de iteraciones a ejecutar.
     * @param subsetSize    Tamaño de cada subconjunto de pedidos por iteración.
     * @param startIteration Iteración desde la cual comenzar (0-based). Si es 0, comienza desde la primera iteración.
     */
    public static void executeForEachSubset(List<Order> orders, List<Vehicle> vehicles, int iterations, int subsetSize, int startIteration) {
        // Validar los parámetros de entrada
        if (orders == null || vehicles == null) {
            throw new IllegalArgumentException("Las listas de pedidos y vehículos no pueden ser nulas.");
        }
        if (subsetSize <= 0) {
            throw new IllegalArgumentException("El tamaño del subconjunto debe ser mayor que 0.");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("El número de iteraciones debe ser mayor que 0.");
        }

        // Validar y ajustar el índice de inicio
        if (startIteration < 0 || startIteration >= iterations) {
            logger.warning("startIteration (" + startIteration + ") está fuera de rango. Comenzando desde la primera iteración (0).");
            startIteration = 0;
        }

        logger.info("Comenzando el procesamiento desde la iteración " + (startIteration + 1));

        for (int iter = startIteration; iter < iterations; iter++) {
            // Calcular los índices de inicio y fin para el subconjunto actual
            int start = iter * subsetSize;
            int end = Math.min(start + subsetSize, orders.size());
            List<Order> availableOrdersSubset = orders.subList(start, end);

            LocalDateTime currentTime = LocalDateTime.now();
            if (!availableOrdersSubset.isEmpty()) {
                logger.info("Iteración " + (iter + 1) + ": Procesando órdenes desde índice " + start + " hasta " + (end - 1) + " (Total: " + availableOrdersSubset.size() + ")");

                // Resetear estado de los vehículos
                resetVehicleStates(vehicles);

                // Asignar órdenes a vehículos
                List<VehicleAssignment> assignments = assignOrdersToVehicles(availableOrdersSubset, vehicles, currentTime);

                // Filtrar asignaciones por destino único
                List<VehicleAssignment> filteredAssignments = filterUniqueDestinations(assignments);

                // Crear una instancia del monitor
                ResourceMonitor monitor = new ResourceMonitor();
                long durationInMillis = 0;
                totalDeliveryTime = 0;

                try {
                    // Iniciar el monitoreo de recursos
                    monitor.startMonitoring();

                    // Tomar el tiempo inicial antes de iniciar la resolución
                    long startTime = System.nanoTime();

                    // Crear el modelo de datos para OR-Tools
                    DataModel data = new DataModel(timeMatrix, new ArrayList<>(), filteredAssignments, locationIndices, locationNames, locationUbigeos);
                    RoutingIndexManager manager = createRoutingIndexManager(data, data.starts, data.ends);
                    RoutingModel routing = createRoutingModel(manager, data);
                    RoutingModel alternativeRouting = null;
                    boolean usedAlternativeStrategy = false;

                    // Intentar resolver con la estrategia CHRISTOFIDES
                    RoutingSearchParameters searchParameters = createSearchParameters(FirstSolutionStrategy.Value.CHRISTOFIDES);
                    logger.info("Resolviendo con estrategia CHRISTOFIDES.");
                    logger.info("Iniciando la resolución del modelo de rutas para el conjunto completo.");
                    Assignment solution = routing.solveWithParameters(searchParameters);

                    // Si no se encuentra solución, reintentar con PATH_CHEAPEST_ARC
                    if (solution == null) {
                        logger.info("No se encontró solución con CHRISTOFIDES. Reintentando con PATH_CHEAPEST_ARC.");
                        RoutingSearchParameters alternativeSearchParameters = createSearchParameters(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC);
                        alternativeRouting = createRoutingModel(manager, data);
                        solution = alternativeRouting.solveWithParameters(alternativeSearchParameters);
                        usedAlternativeStrategy = true;
                    }

                    if (solution != null) {
                        if (usedAlternativeStrategy) {
                            routing = alternativeRouting;
                        }
                        logger.info("Solución encontrada para el conjunto completo.");
                        printSolution(data, routing, manager, solution);
                        logger.info("Rutas calculadas.");
                    } else {
                        logger.info("No se encontró solución para el conjunto completo. Iniciando la división del conjunto...");
                        // Definir las estrategias a intentar en orden
                        List<FirstSolutionStrategy.Value> strategies = Arrays.asList(
                                FirstSolutionStrategy.Value.CHRISTOFIDES,
                                FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC
                        );
                        List<SolutionData> solutions = Collections.synchronizedList(new ArrayList<>());
                        divideAndSolve(filteredAssignments, strategies, solutions); // Llamada recursiva si falla la resolución completa

                        for (SolutionData solutionData : solutions) {
                            logger.info("Solución encontrada en subconjunto.");
                            printSolutionData(solutionData);
                        }
                    }

                    // Tomar el tiempo final después de completar todo
                    long endTime = System.nanoTime();

                    // Calcular el tiempo transcurrido en milisegundos
                    durationInMillis = (endTime - startTime) / 1_000_000;

                    logger.info("Tiempo transcurrido en resolver las rutas: " + durationInMillis + " ms");

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error en la iteración " + (iter + 1), e);
                } finally {
                    // Detener el monitoreo de recursos
                    monitor.stopMonitoring();
                }

                // Guardar las gráficas y el uso de recursos (puedes habilitar o deshabilitar las imágenes)
                generateGraphsForIteration(monitor, subsetSize, iter + 1, false);

                // Guardar las métricas de ejecución en un archivo txt
                saveExecutionMetricsForIteration((long) totalDeliveryTime, durationInMillis, subsetSize, iter + 1);

                // Imprimir el tiempo total de entrega acumulado (asegúrate de que esta variable se actualice correctamente)
                logger.info("Tiempo total de entrega acumulado: " + totalDeliveryTime + " minutos.");
            } else {
                logger.info("No hay órdenes disponibles en este momento para la iteración " + (iter + 1) + ".");
            }
        }
    }

        /**
         * Resetea el estado de todos los vehículos a 'EN_ALMACEN' y los marca como disponibles.
         *
         * @param vehicles Lista de vehículos a resetear.
         */
        private static void resetVehicleStates(List<Vehicle> vehicles) {
            for (Vehicle vehicle : vehicles) {
                vehicle.setEstado(Vehicle.EstadoVehiculo.EN_ALMACEN);
                vehicle.setAvailable(true);
            }
            logger.info("Estados de vehículos reseteados.");
        }

        /**
         * Filtra las asignaciones para asegurar que cada destino sea único.
         *
         * @param assignments Lista de asignaciones de vehículos.
         * @return Lista filtrada de asignaciones con destinos únicos.
         */
        private static List<VehicleAssignment> filterUniqueDestinations(List<VehicleAssignment> assignments) {
            Map<String, VehicleAssignment> uniqueDestinationMap = new HashMap<>();
            for (VehicleAssignment assignment : assignments) {
                String destination = assignment.getOrder().getDestinationUbigeo();
                uniqueDestinationMap.putIfAbsent(destination, assignment);
            }
            return new ArrayList<>(uniqueDestinationMap.values());
        }

    public static void generateGraphsForIteration(ResourceMonitor monitor, int subsetSize, int iteration, boolean generateImages) {
        // Obtiene los datos del monitor
        List<Long> timestamps = monitor.getTimestamps();
        List<Double> cpuUsages = monitor.getCpuUsages();
        List<Long> memoryUsages = monitor.getMemoryUsages();

        // Calcular el total para calcular el promedio luego
        double totalCpuUsage = 0;
        long totalMemoryUsage = 0;

        for (int i = 0; i < timestamps.size(); i++) {
            totalCpuUsage += cpuUsages.get(i);
            totalMemoryUsage += memoryUsages.get(i);
        }

        // Si se desea generar imágenes
        if (generateImages) {
            // Genera la gráfica de CPU
            TimeSeries cpuSeries = new TimeSeries("CPU Usage");
            for (int i = 0; i < timestamps.size(); i++) {
                cpuSeries.addOrUpdate(new Millisecond(new Date(timestamps.get(i))), cpuUsages.get(i));
            }
            TimeSeriesCollection cpuDataset = new TimeSeriesCollection(cpuSeries);
            JFreeChart cpuChart = ChartFactory.createTimeSeriesChart(
                    "Consumo de CPU",
                    "Tiempo",
                    "Uso de CPU (%)",
                    cpuDataset,
                    false,
                    false,
                    false
            );

            // Guarda la gráfica de CPU como imagen
            try {
                ChartUtils.saveChartAsPNG(new File("cpu_usage_subset_" + subsetSize + "_iter_" + iteration + ".png"), cpuChart, 800, 600);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Genera la gráfica de Memoria
            TimeSeries memorySeries = new TimeSeries("Memory Usage");
            for (int i = 0; i < timestamps.size(); i++) {
                memorySeries.addOrUpdate(new Millisecond(new Date(timestamps.get(i))), memoryUsages.get(i));
            }
            TimeSeriesCollection memoryDataset = new TimeSeriesCollection(memorySeries);
            JFreeChart memoryChart = ChartFactory.createTimeSeriesChart(
                    "Consumo de Memoria",
                    "Tiempo",
                    "Uso de Memoria (MB)",
                    memoryDataset,
                    false,
                    false,
                    false
            );

            // Guarda la gráfica de Memoria como imagen
            try {
                ChartUtils.saveChartAsPNG(new File("memory_usage_subset_" + subsetSize + "_iter_" + iteration + ".png"), memoryChart, 800, 600);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Calcular las medias
        double averageCpuUsage = totalCpuUsage / cpuUsages.size();
        double averageMemoryUsage = (double) totalMemoryUsage / memoryUsages.size();

        // Formato de decimales para las medias
        DecimalFormat df = new DecimalFormat("#.##");

        // Crear un archivo .txt con los datos y las medias
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("resource_usage_subset_" + subsetSize + "_iter_" + iteration + ".txt"))) {
            writer.write("Timestamp (ms), CPU Usage (%), Memory Usage (MB)\n");
            for (int i = 0; i < timestamps.size(); i++) {
                writer.write(timestamps.get(i) + ", " + cpuUsages.get(i) + ", " + memoryUsages.get(i) + "\n");
            }
            writer.write("\n--- Estadísticas Finales ---\n");
            writer.write("Media de Uso de CPU: " + df.format(averageCpuUsage) + " %\n");
            writer.write("Media de Uso de Memoria: " + df.format(averageMemoryUsage) + " MB\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveExecutionMetricsForIteration(long totalDeliveryTime, long durationInMillis, int subsetSize, int iteration) {
        // Crear un archivo .txt con los tiempos y el total de entrega acumulado
        String fileName = "execution_metrics_subset_" + subsetSize + "_iter_" + iteration + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write("--- Métricas de Ejecución ---\n");
            writer.write("Tiempo total de entrega acumulado: " + totalDeliveryTime + " minutos\n");
            writer.write("Tiempo transcurrido en resolver las rutas: " + durationInMillis + " ms\n");
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Archivo de métricas de ejecución guardado exitosamente.");
    }



    public static void saveExecutionMetrics(long totalDeliveryTime, long durationInMillis) {
        // Crear un archivo .txt con los tiempos y el total de entrega acumulado
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("execution_metrics.txt", true))) {
            writer.write("--- Métricas de Ejecución ---\n");
            writer.write("Tiempo total de entrega acumulado: " + totalDeliveryTime + " minutos\n");
            writer.write("Tiempo transcurrido en resolver las rutas: " + durationInMillis + " ms\n");
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Archivo de métricas de ejecución guardado exitosamente.");
    }

    public static void generateGraphs(ResourceMonitor monitor) {
        // Obtiene los datos del monitor
        List<Long> timestamps = monitor.getTimestamps();
        List<Double> cpuUsages = monitor.getCpuUsages();
        List<Long> memoryUsages = monitor.getMemoryUsages();

        // Inicializa acumuladores para calcular la media
        double totalCpuUsage = 0;
        long totalMemoryUsage = 0;

        // Genera la gráfica de CPU
        TimeSeries cpuSeries = new TimeSeries("CPU Usage");
        for (int i = 0; i < timestamps.size(); i++) {
            cpuSeries.addOrUpdate(new Millisecond(new Date(timestamps.get(i))), cpuUsages.get(i));
            totalCpuUsage += cpuUsages.get(i);  // Suma para calcular la media
        }
        TimeSeriesCollection cpuDataset = new TimeSeriesCollection(cpuSeries);
        JFreeChart cpuChart = ChartFactory.createTimeSeriesChart(
                "Consumo de CPU",
                "Tiempo",
                "Uso de CPU (%)",
                cpuDataset,
                false,
                false,
                false
        );

        // Guarda la gráfica de CPU como imagen
        try {
            ChartUtils.saveChartAsPNG(new File("cpu_usage.png"), cpuChart, 800, 600);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Genera la gráfica de Memoria
        TimeSeries memorySeries = new TimeSeries("Memory Usage");
        for (int i = 0; i < timestamps.size(); i++) {
            memorySeries.addOrUpdate(new Millisecond(new Date(timestamps.get(i))), memoryUsages.get(i));
            totalMemoryUsage += memoryUsages.get(i);  // Suma para calcular la media
        }
        TimeSeriesCollection memoryDataset = new TimeSeriesCollection(memorySeries);
        JFreeChart memoryChart = ChartFactory.createTimeSeriesChart(
                "Consumo de Memoria",
                "Tiempo",
                "Uso de Memoria (MB)",
                memoryDataset,
                false,
                false,
                false
        );

        // Guarda la gráfica de Memoria como imagen
        try {
            ChartUtils.saveChartAsPNG(new File("memory_usage.png"), memoryChart, 800, 600);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Calcular las medias
        double averageCpuUsage = totalCpuUsage / cpuUsages.size();
        double averageMemoryUsage = (double) totalMemoryUsage / memoryUsages.size();

        // Formato de decimales para las medias
        DecimalFormat df = new DecimalFormat("#.##");

        // Crear un archivo .txt con los datos y las medias
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("resource_usage.txt"))) {
            writer.write("Timestamp (ms), CPU Usage (%), Memory Usage (MB)\n");
            for (int i = 0; i < timestamps.size(); i++) {
                writer.write(timestamps.get(i) + ", " + cpuUsages.get(i) + ", " + memoryUsages.get(i) + "\n");
            }
            writer.write("\n--- Estadísticas Finales ---\n");
            writer.write("Media de Uso de CPU: " + df.format(averageCpuUsage) + " %\n");
            writer.write("Media de Uso de Memoria: " + df.format(averageMemoryUsage) + " MB\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Gráficas y archivo de datos generados exitosamente.");
    }

    private static long getProcessCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        return (long) (osBean.getProcessCpuLoad() * 100); // Porcentaje de CPU usado
    }

    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // Memoria usada en MB
    }

    /**
     * Método optimizado para dividir y resolver las asignaciones de vehículos.
     */
    public static void divideAndSolve(List<VehicleAssignment> assignments,
                                      List<FirstSolutionStrategy.Value> strategies,
                                      List<SolutionData> solutions) {
        if (assignments == null || strategies == null || solutions == null) {
            throw new IllegalArgumentException("Los argumentos no pueden ser nulos.");
        }

        // Utilizar una colección thread-safe para almacenar las soluciones
        List<SolutionData> threadSafeSolutions = Collections.synchronizedList(solutions);

        // Crear un ExecutorService con un número de hilos fijo
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        int maxDepth = 10;
        try {
            // Iniciar el procesamiento del conjunto completo
            Future<?> future = executor.submit(() ->
                    processSubset(assignments, strategies, threadSafeSolutions, executor, 0, maxDepth)
            );

            // Esperar a que todas las tareas se completen
            future.get();

        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error en la ejecución del proceso de resolución.", e);
        } finally {
            // Apagar el ExecutorService de manera ordenada
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Método auxiliar para procesar un subconjunto de asignaciones.
     * Intenta resolver el subconjunto con las estrategias proporcionadas.
     * Si no se puede resolver, divide el subconjunto y procesa recursivamente.
     *
     * @param subset        El subconjunto de asignaciones a procesar.
     * @param strategies    La lista de estrategias a intentar.
     * @param solutions     La lista thread-safe donde se almacenarán las soluciones.
     * @param executor      El ExecutorService para manejar tareas concurrentes.
     */
    private static void processSubset(List<VehicleAssignment> subset,
                                      List<FirstSolutionStrategy.Value> strategies,
                                      List<SolutionData> solutions,
                                      ExecutorService executor,
                                      int depth,
                                      int maxDepth) {
        if (depth > maxDepth) {
            logger.warning("Profundidad máxima alcanzada. Deteniendo la división de subconjuntos.");
            return;
        }

        if (subset.size() <= 1) {
            logger.info("No se puede dividir más. Pedido conflictivo detectado.");
            return;
        }

        // Iterar sobre las estrategias en orden
        for (FirstSolutionStrategy.Value strategy : strategies) {
            logger.info("Intentando resolver subconjunto con estrategia: " + strategy);

            // Intentar resolver el subconjunto con la estrategia actual
            RoutingResult result = solveSubset(subset, strategy);

            if (result != null && result.solution != null) {
                logger.info("Solución encontrada para el subconjunto con estrategia: " + strategy);

                // Extraer y agregar la solución
                SolutionData solutionData = extractSolutionData(result.data, result.routingModel, result.manager, result.solution);
                solutions.add(solutionData);

                // Estrategia exitosa, no es necesario probar más estrategias para este subconjunto
                return;
            } else {
                logger.info("No se encontró solución para el subconjunto con estrategia: " + strategy);
            }
        }

        // Si ninguna estrategia resolvió el subconjunto, dividirlo nuevamente
        logger.info("Todas las estrategias fallaron para el subconjunto. Dividiendo nuevamente...");

        int mid = subset.size() / 2;
        List<VehicleAssignment> firstHalf = new ArrayList<>(subset.subList(0, mid));
        List<VehicleAssignment> secondHalf = new ArrayList<>(subset.subList(mid, subset.size()));

        // Procesar cada mitad de manera concurrente
        Future<?> futureFirst = executor.submit(() ->
                processSubset(firstHalf, strategies, solutions, executor, depth + 1, maxDepth)
        );

        Future<?> futureSecond = executor.submit(() ->
                processSubset(secondHalf, strategies, solutions, executor, depth + 1, maxDepth)
        );

        try {
            // Esperar a que ambas mitades se procesen
            futureFirst.get();
            futureSecond.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.SEVERE, "Error al procesar los subconjuntos divididos.", e);
        }
    }

    /**
     * Método auxiliar para resolver un subconjunto con una estrategia específica.
     *
     * @param subset   El subconjunto de asignaciones a resolver.
     * @param strategy La estrategia a aplicar.
     * @return El resultado de la resolución, o null si no se encontró solución.
     */
    private static RoutingResult solveSubset(List<VehicleAssignment> subset, FirstSolutionStrategy.Value strategy) {
        try {
            DataModel data = new DataModel(timeMatrix, new ArrayList<>(), subset, locationIndices, locationNames, locationUbigeos);
            RoutingIndexManager manager = createRoutingIndexManager(data, data.starts, data.ends);
            RoutingModel routing = createRoutingModel(manager, data);
            RoutingSearchParameters searchParameters = createSearchParameters(strategy);

            Assignment solution = routing.solveWithParameters(searchParameters);

            if (solution != null) {
                logger.info("Solución encontrada para el subconjunto con estrategia: " + strategy);
                return new RoutingResult(solution, routing, manager, data);
            } else {
                logger.info("No se encontró solución para el subconjunto con estrategia: " + strategy);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error al resolver el subconjunto con estrategia: " + strategy, e);
            return null;
        }
    }


    // Método para extraer los datos de la solución en una estructura Java pura
    private static SolutionData extractSolutionData(DataModel data,
                                                    RoutingModel routing,
                                                    RoutingIndexManager manager,
                                                    Assignment solution) {
        SolutionData solutionData = new SolutionData();
        solutionData.objectiveValue = solution.objectiveValue();
        solutionData.routes = new HashMap<>();
        long maxRouteTime = 0;  // Variable para almacenar el tiempo máximo entre todas las rutas
        long localTotalTime = 0;  // Variable local para almacenar el tiempo total de esta solución

        for (int i = 0; i < data.vehicleNumber; ++i) {
            String vehicleCode = "Vehículo_" + i; // Puedes adaptar para obtener el código del vehículo real si está disponible
            List<RouteSegment> route = new ArrayList<>();
            long routeTime = 0;  // Variable para almacenar el tiempo total de la ruta actual

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

                // Agregar el tiempo de viaje al total de la ruta
                routeTime += durationMinutes;

                // Agregar el segmento de ruta a la lista
                route.add(new RouteSegment(fromName + " to " + toName, toUbigeo, distance, durationMinutes));

                // Avanzar al siguiente nodo
                index = nextIndex;
            }

            // Almacenar la ruta calculada para el vehículo actual
            solutionData.routes.put(vehicleCode, route);

            // Guardar el tiempo total de la ruta
            solutionData.routeTimes.put(vehicleCode, routeTime);

            // Comparar para obtener el máximo tiempo entre todas las rutas
            maxRouteTime = Math.max(maxRouteTime, routeTime);

            // Sumar el tiempo total de esta ruta al tiempo local total de la solución
            localTotalTime += routeTime;
        }

        // Almacenar el tiempo máximo de todas las rutas en la solución
        solutionData.maxRouteTime = maxRouteTime;

        // Sumar el tiempo local total de esta solución al contador global
        synchronized (Main.class) {
            totalDeliveryTime += localTotalTime;
        }

        return solutionData;
    }


    // Estructura para almacenar los datos de la solución
    static class SolutionData {
        public long objectiveValue;
        public Map<String, List<RouteSegment>> routes; // Mapa de rutas por vehículo
        public Map<String, Long> routeTimes;            // Mapa para almacenar el tiempo total de cada ruta
        public long maxRouteTime;

        public SolutionData() {
            this.routes = new HashMap<>();
            this.routeTimes = new HashMap<>();
            this.maxRouteTime = 0;
        }
    }

    // Método para imprimir los datos de la solución almacenados
    private static void printSolutionData(SolutionData solutionData) {
        logger.info("Objetivo de la Solución: " + solutionData.objectiveValue);

        long maxRouteTime = solutionData.maxRouteTime; // Recuperamos el tiempo máximo almacenado
        long localTotalTime = 0;  // Variable local para almacenar el tiempo total de esta solución

        for (Map.Entry<String, List<RouteSegment>> entry : solutionData.routes.entrySet()) {
            String vehicleCode = entry.getKey();
            List<RouteSegment> routeSegments = entry.getValue();

            logger.info("\n--- Ruta para el " + vehicleCode + " ---");

            // Variable para acumular el tiempo total de la ruta
            long routeTime = solutionData.routeTimes.get(vehicleCode);

            // Imprimir los segmentos de la ruta
            int routeStep = 1;
            for (RouteSegment segment : routeSegments) {
                logger.info(String.format(
                        "%d. De %s: Duración %d minutos, Distancia %.2f km",
                        routeStep,
                        segment.getName(),
                        segment.getDurationMinutes(),
                        segment.getDistance()
                ));
                routeStep++;
            }

            // Imprimir el tiempo total de la ruta
            logger.info("Tiempo total de la ruta: " + formatTime(routeTime));
            localTotalTime += routeTime;
        }

        totalDeliveryTime += localTotalTime;

        // Imprimir el tiempo máximo entre todas las rutas
        logger.info("Máximo tiempo de las rutas: " + formatTime(maxRouteTime));
    }


    private static void printRelevantTimeMatrix(DataModel data, String origin, String destination) {
        int originIndex = data.locationUbigeos.indexOf(origin);
        int destIndex = data.locationUbigeos.indexOf(destination);

        if (originIndex != -1 && destIndex != -1) {
            long time = data.timeMatrix[originIndex][destIndex];
            logger.info("Tiempo en la matriz para " + origin + " a " + destination + ": " + formatTime(time));
        } else {
            logger.info("No se encontró una o ambas ubicaciones en la matriz.");
        }
    }

    private static void runSimulation(long[][] timeMatrix, List<Order> allOrders, List<Vehicle> vehicles,
                                      Map<String, Integer> locationIndices, List<String> locationNames,
                                      List<String> locationUbigeos, Map<String, Location> locations,
                                      RouteCache routeCache, List<Blockage> blockages, List<Maintenance> maintenanceSchedule) {
        SimulationState state = initializeSimulation(allOrders, vehicles, locations, routeCache, timeMatrix, blockages, maintenanceSchedule);
        Map<String, List<RouteSegment>> vehicleRoutes = new HashMap<>();
        ScheduledExecutorService executorService = setupExecutors();

        try {
            runSimulationLoop(state, timeMatrix, allOrders, locationIndices, locationNames, locationUbigeos,
                    vehicleRoutes, executorService, blockages);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Simulación interrumpida", e);
        } finally {
            shutdownExecutors(executorService);
        }
    }

    private static SimulationState initializeSimulation(List<Order> allOrders, List<Vehicle> vehicles, Map<String,
            Location> locations, RouteCache routeCache, long[][] timeMatrix, List<Blockage> blockages, List<Maintenance> maintenanceSchedule) {
        LocalDateTime initialSimulationTime = determineInitialSimulationTime(allOrders);
        Map<String, Vehicle> vehicleMap = createVehicleMap(vehicles);
        return new SimulationState(vehicleMap, initialSimulationTime, allOrders, locations, routeCache,
                timeMatrix, blockages, maintenanceSchedule);
    }

    private static LocalDateTime determineInitialSimulationTime(List<Order> allOrders) {
        return allOrders.stream()
                .map(Order::getOrderTime)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private static Map<String, Vehicle> createVehicleMap(List<Vehicle> vehicles) {
        return vehicles.stream().collect(Collectors.toMap(Vehicle::getCode, v -> v));
    }

    private static ScheduledExecutorService setupExecutors() {
        return Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 2);
    }

    private static void runSimulationLoop(SimulationState state, long[][] timeMatrix, List<Order> allOrders,
                                          Map<String, Integer> locationIndices, List<String> locationNames,
                                          List<String> locationUbigeos, Map<String, List<RouteSegment>> vehicleRoutes,
                                          ScheduledExecutorService executorService,
                                          List<Blockage> blockages) throws InterruptedException {
        LocalDateTime endTime = state.getCurrentTime().plusDays(SIMULATION_DAYS);
        AtomicBoolean isSimulationRunning = new AtomicBoolean(true);

        // Iniciar el servidor de averías
        BreakdownServer breakdownServer = new BreakdownServer(state);
        breakdownServer.start();

        scheduleTimeAdvancement(state, endTime, isSimulationRunning, vehicleRoutes, executorService, blockages);
        schedulePlanning(state, allOrders, locationIndices, locationNames, locationUbigeos, vehicleRoutes, executorService, isSimulationRunning);

        while (isSimulationRunning.get()) {
            //state.checkForBreakdownCommands();
            Thread.sleep(1000);
        }
    }

    private static void scheduleTimeAdvancement(SimulationState state, LocalDateTime endTime, AtomicBoolean isSimulationRunning,
                                                Map<String, List<RouteSegment>> vehicleRoutes,
                                                ScheduledExecutorService executorService, List<Blockage> allBlockages) {
        executorService.scheduleAtFixedRate(() -> {
            try {
                if (!isSimulationRunning.get()) return;

                state.setCurrentTime(state.getCurrentTime().plusMinutes(TIME_ADVANCEMENT_INTERVAL_MINUTES));
                logger.info("Tiempo de simulación: " + state.getCurrentTime());

                state.updateBlockages(state.getCurrentTime(), allBlockages);
                state.updateVehicleStates(vehicleRoutes);
                state.updateOrderStatuses();
                logger.info("Estados de vehículos, pedidos y bloqueos actualizados.");

                if (state.getCurrentTime().isAfter(endTime)) {
                    logger.info("Simulación completada.");
                    isSimulationRunning.set(false);
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
            if (!isSimulationRunning.get()) return;

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

    private static void logAvailableOrders(List<Order> availableOrders) {
        logger.info("Órdenes disponibles: " + availableOrders.size());
        for (Order order : availableOrders) {
            logger.info("Orden " + order.getId() + " - Paquetes restantes sin asignar: " + order.getUnassignedPackages());
        }
    }

    private static void calculateAndApplyRoutes(long[][] currentTimeMatrix, List<VehicleAssignment> assignments,
                                                Map<String, Integer> locationIndices, List<String> locationNames,
                                                List<String> locationUbigeos, Map<String, List<RouteSegment>> vehicleRoutes,
                                                SimulationState state, ExecutorService executorService) {
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

    private static void shutdownExecutors(ExecutorService executorService) {
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    private static Map<String, List<RouteSegment>> applyRouteToVehicles(RoutingIndexManager manager, DataModel data,
                                                                        List<VehicleAssignment> assignments, RoutingModel routing,
                                                                        Assignment solution, SimulationState state) {
        Map<String, List<RouteSegment>> vehicleRoutes = new HashMap<>();
        for (int i = 0; i < assignments.size(); ++i) {
            VehicleAssignment assignment = assignments.get(i);
            Vehicle vehicle = assignment.getVehicle();
            Order order = assignment.getOrder();

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

                long duration = data.timeMatrix[fromNode][toNode];
                double distance = calculateDistanceFromNodes(data, fromNode, toNode);

                route.add(new RouteSegment(fromName + " to " + toName, toUbigeo, distance, duration));

                index = nextIndex;
            }

            vehicle.setRoute(route);
            vehicle.startJourney(state.getCurrentTime(), order);
            vehicleRoutes.put(vehicle.getCode(), route);
            logger.info("Vehículo " + vehicle.getCode() + " iniciando viaje a " + order.getDestinationUbigeo());
        }
        return vehicleRoutes;
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

                route.add(new RouteSegment(fromName + " to " + toName, toUbigeo, distance, durationMinutes));

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

    private static List<Order> getAvailableOrders(List<Order> allOrders, LocalDateTime currentTime) {
        return allOrders.stream()
                .filter(order -> (order.getStatus() == Order.OrderStatus.REGISTERED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ASSIGNED
                        || order.getStatus() == Order.OrderStatus.PARTIALLY_ARRIVED)
                        && !order.getOrderTime().isAfter(currentTime))
                .collect(Collectors.toList());
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

    private static void scheduleVehicleAvailability(Vehicle vehicle, LocalDateTime dueTime, LocalDateTime currentTime) {
        // Calcular cuánto tiempo falta en la simulación hasta que el vehículo esté disponible
        long delayInSimulationMinutes = java.time.Duration.between(currentTime, dueTime).toMinutes();

        // Convertir minutos de simulación a milisegundos reales
        long delayInRealMillis = delayInSimulationMinutes * 1000L / SIMULATION_SPEED;

        if (delayInRealMillis < 0) {
            delayInRealMillis = 0;
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            vehicle.setAvailable(true);
            logger.info("Vehículo " + vehicle.getCode() + " está disponible nuevamente.");
            executor.shutdown();
        }, delayInRealMillis, TimeUnit.MILLISECONDS);
    }

    private static List<Vehicle> getAvailableVehicles(List<Vehicle> vehicles, String locationUbigeo) {
        // Loguear el origen del ubigeo de la orden antes del filtrado
        logger.info(String.format("Ubigeo de origen de la orden: %s", locationUbigeo));

        return vehicles.stream()
                .peek(v -> logger.info(String.format("Ubigeo actual del vehículo %s: %s", v.getCode(), v.getCurrentLocationUbigeo())))
                .filter(v -> v.getEstado() == Vehicle.EstadoVehiculo.EN_ALMACEN && v.getCurrentLocationUbigeo().equals(locationUbigeo))
                .collect(Collectors.toList());
    }

    private static void calculateRouteInSeparateThread(DataModel data, SimulationState state, Map<String, List<RouteSegment>> vehicleRoutes) {
        ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
        Future<Map<String, List<RouteSegment>>> routeFuture = routeExecutor.submit(() -> {
            try {
                return calculateRoute(data, data.starts, data.ends, state);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error durante el cálculo de rutas", e);
                return null;
            }
        });

        try {
            Map<String, List<RouteSegment>> newRoutes = routeFuture.get(5, TimeUnit.MINUTES); // Espera hasta 5 minutos
            if (newRoutes != null) {
                vehicleRoutes.putAll(newRoutes);
                logger.info("Cálculo de rutas completado con éxito y rutas actualizadas.");
            } else {
                logger.warning("El cálculo de rutas no produjo resultados.");
            }
        } catch (TimeoutException e) {
            logger.warning("El cálculo de rutas excedió el tiempo límite de 5 minutos.");
            routeFuture.cancel(true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error inesperado durante el cálculo de rutas", e);
        } finally {
            routeExecutor.shutdownNow();
        }
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

    private static Map<String, List<RouteSegment>> calculateMissingRoutes(DataModel data, int[] start, int[] end, SimulationState state) {
        RoutingIndexManager manager = createRoutingIndexManager(data, start, end);
        RoutingModel routing = createRoutingModel(manager, data);
        RoutingSearchParameters searchParameters = createSearchParameters();

        logger.info("Iniciando la resolución del modelo de rutas.");
        Assignment solution = routing.solveWithParameters(searchParameters);
        logger.info("Solución de rutas obtenida.");

        if (solution != null) {
            Map<String, List<RouteSegment>> calculatedRoutes = applyRouteToVehicles(manager, data, data.assignments, routing, solution, state);
            printSolution(data, routing, manager, solution);
            logger.info("Solución de rutas impresa correctamente.");
            return calculatedRoutes;
        } else {
            logger.warning("No se encontró solución.");
            return new HashMap<>();
        }
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

    public static RoutingSearchParameters createSearchParameters(FirstSolutionStrategy.Value firstSolutionStrategy) {
        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(firstSolutionStrategy)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(10).build())
                //.setLogSearch(true)  // Habilitar "verbose logging"
                .build();
        logger.info("Parámetros de búsqueda configurados con estrategia: " + firstSolutionStrategy);
        return searchParameters;
    }

    /*private static void updateRouteCache(DataModel data, int[] start, int[] end, Map<String, List<RouteSegment>> calculatedRoutes) {
        for (int i = 0; i < data.vehicleNumber; i++) {
            String vehicleCode = data.assignments.get(i).getVehicle().getCode();
            if (calculatedRoutes.containsKey(vehicleCode)) {
                String fromUbigeo = data.locationUbigeos.get(start[i]);
                String toUbigeo = data.locationUbigeos.get(end[i]);
                List<RouteSegment> route = calculatedRoutes.get(vehicleCode);
                routeCache.putRoute(fromUbigeo, toUbigeo, route);
                logger.info("Ruta calculada y almacenada en caché para " + fromUbigeo + " -> " + toUbigeo);
                logCachedRoute(data, vehicleCode, fromUbigeo, toUbigeo, route);
            }
        }
    }*/

    private static void logCachedRoute(DataModel data, String vehicleCode, String fromUbigeo, String toUbigeo, List<RouteSegment> route) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n--- Ruta calculada y almacenada en caché ---\n");
        logBuilder.append("Código del Vehículo: ").append(vehicleCode).append("\n");
        logBuilder.append("Origen (Ubigeo): ").append(fromUbigeo).append("\n");
        logBuilder.append("Destino (Ubigeo): ").append(toUbigeo).append("\n");
        logBuilder.append("Segmentos de la ruta:\n");

        long totalRouteTimeMinutes = 0;
        for (int i = 0; i < route.size(); i++) {
            RouteSegment segment = route.get(i);
            totalRouteTimeMinutes += segment.getDurationMinutes();

            logBuilder.append(String.format(
                    "%d. De %s a %s: %s, Distancia: %.2f km\n",
                    i + 1,
                    segment.getName().split(" to ")[0],
                    segment.getName().split(" to ")[1],
                    formatTime(segment.getDurationMinutes()), // Convertir minutos a segundos para formatTime
                    segment.getDistance()
            ));
        }

        logBuilder.append("Tiempo total de la ruta: ").append(formatTime(totalRouteTimeMinutes)).append("\n");
        logBuilder.append("-----------------------------");
        logger.info(logBuilder.toString());
    }

    private static boolean isStartOrEndNode(int node, int[] starts, int[] ends) {
        for (int i = 0; i < starts.length; i++) {
            if (node == starts[i] || node == ends[i]) {
                return true;
            }
        }
        return false;
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
        totalDeliveryTime = localTotalTime;
    }

}