package com.odiparpack;

import com.odiparpack.models.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.odiparpack.Main.logger;

public class DataLoader {
    private static final double TIME_UNIT = 60000.0; // 1 minuto en milisegundos
    // Mapa que relaciona ubigeos con nombres de ubicaciones
    public static Map<String, String> ubigeoToNameMap = new HashMap<>();

    public Map<String, Location> loadLocations(String filePath) {
        Map<String, Location> locations = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length < 7) continue; // Asegurarse de que hay suficientes campos
                String ubigeo = parts[0].trim();
                String department = parts[1].trim();
                String province = parts[2].trim();
                double latitude = Double.parseDouble(parts[3].trim());
                double longitude = Double.parseDouble(parts[4].trim());
                String naturalRegion = parts[5].trim();
                int warehouseCapacity = Integer.parseInt(parts[6].trim());

                Location location = new Location(ubigeo, department, province, latitude, longitude, naturalRegion, warehouseCapacity);
                locations.put(ubigeo, location);

                // Población automática de ubigeoToNameMap
                ubigeoToNameMap.put(ubigeo, province);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return locations;
    }

    public List<Vehicle> loadVehicles(String filePath) {
        List<Vehicle> vehicles = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                String code = parts[0].trim();
                String type = parts[1].trim();
                int capacity = Integer.parseInt(parts[2].trim());
                String currentUbigeo = parts[3].trim();

                Vehicle vehicle = new Vehicle(code, type, capacity, currentUbigeo);
                vehicle.setAvailable(true);
                vehicles.add(vehicle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return vehicles;
    }

    public List<Order> loadOrders(String filePath, Map<String, Location> locations) {
        List<Order> orders = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm"); // Solo para la hora
        LocalDate baseDate = LocalDate.now().withDayOfMonth(1);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int orderId = 1;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue; // Ignorar líneas vacías o comentarios

                System.out.println("Procesando línea: " + line);

                String[] parts = line.split(",");
                if (parts.length < 4) {  // Asegurarse de que haya al menos 4 partes
                    System.out.println("Error: línea malformada, no tiene suficientes partes: " + Arrays.toString(parts));
                    continue;
                }

                System.out.println("Partes separadas: " + Arrays.toString(parts));

                // Parsear el día y la hora manualmente
                String dayAndTimeStr = parts[0].trim();
                String[] dayAndTimeParts = dayAndTimeStr.split(" ");
                if (dayAndTimeParts.length != 2) {
                    System.out.println("Error: formato de fecha/hora incorrecto: " + dayAndTimeStr);
                    continue;
                }

                int day;
                LocalTime time;
                try {
                    day = Integer.parseInt(dayAndTimeParts[0].trim());
                    time = LocalTime.parse(dayAndTimeParts[1].trim(), timeFormatter);
                } catch (Exception e) {
                    System.out.println("Error al parsear el día o la hora: " + e.getMessage());
                    continue;
                }

                // Crear la fecha completa usando baseDateTime y los valores de día y hora
                LocalDateTime orderDateTime = LocalDateTime.of(baseDate, time).withDayOfMonth(day);

                String[] locationParts = parts[1].split("=>");
                if (locationParts.length != 2) {
                    System.out.println("Error en la ruta, no tiene dos ubicaciones: " + Arrays.toString(locationParts));
                    continue;
                }

                String originUbigeo = locationParts[0].trim();
                String destinationUbigeo = locationParts[1].trim();
                int quantity;
                try {
                    quantity = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    System.out.println("Error en la cantidad, no es un número: " + parts[2].trim());
                    continue;
                }
                String clientId = parts[3].trim();

                // Verificar que las ubicaciones existan en el mapa
                Location destination = locations.get(destinationUbigeo);
                if (destination == null) {
                    System.out.println("Ubigeo destino no encontrado: " + destinationUbigeo);
                    continue;
                }

                String naturalRegion = destination.getNaturalRegion();
                LocalDateTime dueDateTime = orderDateTime;

                // Ajustar el tiempo de entrega basado en la región
                switch (naturalRegion.toUpperCase()) {
                    case "COSTA":
                        dueDateTime = dueDateTime.plusDays(1);
                        break;
                    case "SIERRA":
                        dueDateTime = dueDateTime.plusDays(2);
                        break;
                    case "SELVA":
                        dueDateTime = dueDateTime.plusDays(3);
                        break;
                    default:
                        dueDateTime = dueDateTime.plusDays(1);
                        break;
                }

                Order order = new Order(orderId++, originUbigeo, destinationUbigeo, quantity,
                        orderDateTime, dueDateTime, clientId);
                orders.add(order);

                // Debugging: print details of each order
                System.out.println("Cargado Pedido: ID: " + order.getId() +
                        ", Origen: " + order.getOriginUbigeo() +
                        ", Destino: " + order.getDestinationUbigeo() +
                        ", Cantidad: " + order.getQuantity() +
                        ", Hora del Pedido: " + order.getOrderTime() +
                        ", Tiempo de Entrega: " + order.getDueTime() +
                        ", ID del Cliente: " + order.getClientId());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Total de pedidos cargados: " + orders.size());
        return orders;
    }

    public List<Edge> loadEdges(String filePath, Map<String, Location> locations) {
        List<Edge> edges = new ArrayList<>();
        //System.out.println("Cargando edges desde: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=>");
                if (parts.length < 2) continue;
                String originUbigeo = parts[0].trim();
                String destinationUbigeo = parts[1].trim();

                //System.out.println("Procesando edge: " + originUbigeo + " => " + destinationUbigeo);

                Location origin = locations.get(originUbigeo);
                Location destination = locations.get(destinationUbigeo);

                if (origin != null && destination != null) {
                    double distance = Utils.calculateDistance(origin.getLatitude(), origin.getLongitude(),
                            destination.getLatitude(), destination.getLongitude());
                    double speed = Utils.getAverageSpeed(origin.getNaturalRegion(), destination.getNaturalRegion());
                    double travelTime = distance / speed; // en horas

                    Edge edge = new Edge(originUbigeo, destinationUbigeo, distance, travelTime);
                    edges.add(edge);
                    //System.out.println("Edge agregado: " + edge);
                } else {
                    System.out.println("¡Advertencia! No se pudo encontrar la ubicación para " + originUbigeo + " o " + destinationUbigeo);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("Total de edges cargados: " + edges.size());
        return edges;
    }

    public List<Blockage> loadBlockages(String filePath) {
        List<Blockage> blockages = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                try {
                    // Formato: UG-Ori => UG-Des;mmdd-inicio,hh:mm-inicio==mmdd-fin,hh:mm-fin
                    String[] parts = line.split(";");
                    if (parts.length < 2) continue;
                    String[] nodes = parts[0].split("=>");
                    String originUbigeo = nodes[0].trim();
                    String destinationUbigeo = nodes[1].trim();

                    String[] times = parts[1].split("==");
                    String startStr = times[0].trim();
                    String endStr = times[1].trim();

                    LocalDateTime startTime = Utils.parseBlockageDateTime(startStr);
                    LocalDateTime endTime = Utils.parseBlockageDateTime(endStr);

                    Blockage blockage = new Blockage(originUbigeo, destinationUbigeo, startTime, endTime);
                    blockages.add(blockage);
                } catch (Exception e) {
                    logger.warning("Error al procesar la línea de bloqueo: " + line + ". Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.severe("Error al leer el archivo de bloqueos: " + e.getMessage());
        }
        return blockages;
    }

    /*public long[][] createTimeMatrix(List<Location> locations, List<Edge> edges) {
        int n = locations.size();
        long[][] timeMatrix = new long[n][n];

        // Inicializar la matriz con valores muy grandes
        for (int i = 0; i < n; i++) {
            Arrays.fill(timeMatrix[i], Long.MAX_VALUE);
            timeMatrix[i][i] = 0; // Distancia a sí mismo es 0
        }

        // Llenar la matriz con los tiempos de viaje conocidos
        for (Edge edge : edges) {
            //System.out.println("Origen: " + edge.getOriginUbigeo() + ", Destino: " + edge.getDestinationUbigeo() + ", Tiempo de Viaje: " + edge.getTravelTime());
            int fromIndex = getLocationIndex(locations, edge.getOriginUbigeo());
            int toIndex = getLocationIndex(locations, edge.getDestinationUbigeo());
            if (fromIndex != -1 && toIndex != -1) {
                long travelTime = (long) (edge.getTravelTime() * 60); // Convertir horas a minutos
                timeMatrix[fromIndex][toIndex] = travelTime;
                timeMatrix[toIndex][fromIndex] = travelTime; // Asumiendo que el viaje es bidireccional
            }
        }

        // Aplicar el algoritmo de Floyd-Warshall para completar la matriz
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (timeMatrix[i][k] != Long.MAX_VALUE && timeMatrix[k][j] != Long.MAX_VALUE) {
                        timeMatrix[i][j] = Math.min(timeMatrix[i][j], timeMatrix[i][k] + timeMatrix[k][j]);
                    }
                }
            }
        }

        // Imprimir
        //printTimeMatrix(timeMatrix);

        return timeMatrix;
    }*/

    public long[][] createTimeMatrix(List<Location> locations, List<Edge> edges) {
        int n = locations.size();
        long[][] timeMatrix = new long[n][n];

        // Inicializar la matriz con valores muy grandes
        for (int i = 0; i < n; i++) {
            Arrays.fill(timeMatrix[i], Long.MAX_VALUE);
            timeMatrix[i][i] = 0; // Distancia a sí mismo es 0
        }

        // Llenar la matriz solo con los tiempos de viaje conocidos
        for (Edge edge : edges) {
            int fromIndex = getLocationIndex(locations, edge.getOriginUbigeo());
            int toIndex = getLocationIndex(locations, edge.getDestinationUbigeo());
            if (fromIndex != -1 && toIndex != -1) {
                long travelTime = (long) (edge.getTravelTime() * 60); // Convertir horas a minutos
                timeMatrix[fromIndex][toIndex] = travelTime;
                timeMatrix[toIndex][fromIndex] = travelTime; // Asumiendo que el viaje es bidireccional
            }
        }

        // Log para imprimir la cantidad de nodos (locations)
        logger.info("Cantidad total de nodos (locations): " + n);

        return timeMatrix;
    }

    private void printTimeMatrix(long[][] timeMatrix) {
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

    private int getLocationIndex(List<Location> locations, String ubigeo) {
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getUbigeo().equals(ubigeo)) {
                return i;
            }
        }
        return -1;
    }

    public List<Maintenance> loadMaintenanceSchedule(String filePath) {
        List<Maintenance> maintenances = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                // Formato: VehicleCode,mmdd-inicio,hh:mm-inicio,mmdd-fin,hh:mm-fin
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                String vehicleCode = parts[0].trim();
                String startStr = parts[1].trim() + "," + parts[2].trim();
                String endStr = parts[3].trim() + "," + parts[4].trim();

                long startTime = Utils.parseBlockageDateTimeToTimestamp(startStr);
                long endTime = Utils.parseBlockageDateTimeToTimestamp(endStr);

                Maintenance maintenance = new Maintenance(vehicleCode, startTime, endTime);
                maintenances.add(maintenance);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return maintenances;
    }

    /**
     * Obtiene un mapa que relaciona ubigeos con nombres de ubicaciones.
     *
     * @return Un mapa inmutable de ubigeo a nombre de ubicación.
     */
    public Map<String, String> getUbigeoToNameMap() {
        return Collections.unmodifiableMap(ubigeoToNameMap);
    }

}
