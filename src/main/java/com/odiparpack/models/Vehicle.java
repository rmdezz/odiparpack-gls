package com.odiparpack.models;

import com.odiparpack.DataLoader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.odiparpack.Main.locations;
import static com.odiparpack.Main.logger;

public class Vehicle {
    public enum EstadoVehiculo {
        HACIA_ALMACEN,
        EN_ALMACEN,
        ORDENES_CARGADAS,
        EN_TRANSITO_ORDEN,
        EN_ESPERA_EN_OFICINA,
        LISTO_PARA_RETORNO,
        AVERIADO_1,
        AVERIADO_2,
        AVERIADO_3,
    }

    private String code;
    private String type; // A, B, C
    private int capacity;
    private String currentLocationUbigeo;
    private boolean isAvailable;
    private String homeUbigeo;
    private VehicleStatus status;
    private boolean isRouteBeingCalculated;

    public EstadoVehiculo getEstado() {
        return estado;
    }

    public void setEstado(EstadoVehiculo estado) {
        this.estado = estado;
    }

    private EstadoVehiculo estado;
    private LocalDateTime departureTime;

    public boolean isListoParaRegresarAlmacen() {
        return listoParaRegresarAlmacen;
    }

    public void setListoParaRegresarAlmacen(boolean listoParaRegresarAlmacen) {
        this.listoParaRegresarAlmacen = listoParaRegresarAlmacen;
    }

    private boolean listoParaRegresarAlmacen;
    private LocalDateTime waitStartTime;
    public static final long WAIT_TIME_MINUTES = 120; // 2 horas

    public LocalDateTime getWaitStartTime() {
        return waitStartTime;
    }

    public void setWaitStartTime(LocalDateTime waitStartTime) {
        this.waitStartTime = waitStartTime;
    }



    public Order getCurrentOrder() {
        return currentOrder;
    }

    public void setCurrentOrder(Order currentOrder) {
        this.currentOrder = currentOrder;
    }

    private Order currentOrder;
    private List<RouteSegment> route;
    private int currentSegmentIndex;

    // Constructor
    public Vehicle(String code, String type, int capacity, String currentLocationUbigeo) {
        this.code = code;
        this.type = type;
        this.capacity = capacity;
        this.currentLocationUbigeo = currentLocationUbigeo;
        this.homeUbigeo = currentLocationUbigeo;
        this.isAvailable = true;
        this.estado = EstadoVehiculo.EN_ALMACEN;
        this.listoParaRegresarAlmacen = false;
    }

    // Getters and setters
    public String getCode() { return code; }
    public String getType() { return type; }
    public int getCapacity() { return capacity; }
    public String getCurrentLocationUbigeo() { return currentLocationUbigeo; }
    public void setCurrentLocationUbigeo(String ubigeo) { this.currentLocationUbigeo = ubigeo; }
    public String getHomeUbigeo() { return homeUbigeo; }
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }
    public void setRoute(List<RouteSegment> route) {
        this.route = new ArrayList<>(route);
    }

    public void updateStatus(LocalDateTime currentTime, WarehouseManager warehouseManager) {
        if (isWaiting(currentTime)) {
            return;
        }

        if (status == null) { // || currentOrder == null
            return;
        }

        if (hasArrivedAtDestination(currentTime)) {
            handleArrivalAtDestination(currentTime, warehouseManager);
        } else {
            updateCurrentSegmentStatus(currentTime);
        }
    }

    private boolean isWaiting(LocalDateTime currentTime) {
        if (waitStartTime != null) {
            long waitedMinutes = ChronoUnit.MINUTES.between(waitStartTime, currentTime);
            if (waitedMinutes >= WAIT_TIME_MINUTES) {
                logger.info(String.format("Vehículo %s ha completado su tiempo de espera de %d minutos en %s",
                        this.getCode(), waitedMinutes, this.currentLocationUbigeo));
                waitStartTime = null;
                setAvailable(true);
                setListoParaRegresarAlmacen(true);
                estado = EstadoVehiculo.LISTO_PARA_RETORNO;
                return false;
            }
            logger.info(String.format("Vehículo %s aún en espera. Tiempo transcurrido: %d minutos",
                    this.getCode(), waitedMinutes));
            return true; // Still waiting
        }
        return false;
    }

    private boolean hasArrivedAtDestination(LocalDateTime currentTime) {
        return currentTime.isAfter(status.getEstimatedArrivalTime()) || currentTime.isEqual(status.getEstimatedArrivalTime());
    }

    private void handleArrivalAtDestination(LocalDateTime currentTime, WarehouseManager warehouseManager) {
        currentSegmentIndex++;
        if (currentSegmentIndex >= route.size()) {
            completeDelivery(currentTime, warehouseManager);
        } else {
            updateCurrentSegment(currentTime);
        }
    }

    // Lo usamos tanto cuanto entrega paquetes como cuando se dirige hacia almacen.
    private void completeDelivery(LocalDateTime currentTime, WarehouseManager warehouseManager) {
        logArrivalAtDestination(currentTime);
        if (estado != EstadoVehiculo.HACIA_ALMACEN) {
            int deliverablePackages = calculateDeliverablePackages();
            updateWarehouseCapacity(warehouseManager, deliverablePackages);
            updateOrderStatus(currentTime, deliverablePackages);
            startWaitingPeriod(currentTime);
        } else {
            String[] segment = status.getCurrentSegment().split(" to ");
            String toName = segment[1];
            currentLocationUbigeo = toName;
            logger.info(String.format("Ubicacion actual actualizada del vehiculo %s a: %s.",
                    getCode(), getCurrentLocationUbigeo()));
        }
        resetVehicleStatus();
    }

    private void logArrivalAtDestination(LocalDateTime currentTime) {
        String arrivalTimeStr = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String departureTimeStr = departureTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder logBuilder = new StringBuilder();

        logBuilder.append("\n--- Vehículo Llegada A Destino Final ---\n");
        logBuilder.append("Código del Vehículo: ").append(this.getCode()).append("\n");
        logBuilder.append("Capacidad del Vehículo: ").append(this.getCapacity()).append(" paquetes\n");

        if (estado == EstadoVehiculo.HACIA_ALMACEN) {
            logBuilder.append("Tipo de Viaje: Hacia Almacén\n");
            logBuilder.append("Origen (Oficina de Entrega): ").append(locations.get(getCurrentLocationUbigeo()).getProvince()).append("\n");

            // Extraer los nombres de las ubicaciones del nombre del segmento
            String[] segment = status.getCurrentSegment().split(" to ");
            String fromName = segment[0];
            String toName = segment[1];
            logBuilder.append("Destino (Almacén): ").append(toName).append("\n");
            //logBuilder.append("  - Ubigeo: ").append(locations.get(toName).getUbigeo()).append("\n");

        } else {
            String dueTimeStr = currentOrder.getDueTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logBuilder.append("Tipo de Viaje: Entrega de Orden\n");
            logBuilder.append("Orden Entregada:\n");
            logBuilder.append("  - Código de la Orden: ").append(currentOrder.getId()).append("\n");
            logBuilder.append("  - Cantidad Total de la Orden: ").append(currentOrder.getQuantity()).append(" paquetes\n");
            logBuilder.append("  - Cantidad Asignada al Vehículo: ").append(currentOrder.getRemainingPackagesToDeliver()).append(" paquetes\n");
            logBuilder.append("Origen: ").append(currentOrder.getOriginUbigeo()).append("\n");

            String destinoUbigeo = currentOrder.getDestinationUbigeo();
            Location destinoLocation = locations.get(destinoUbigeo);

            if (destinoLocation != null) {
                String destinoNombre = destinoLocation.getProvince();
                logBuilder.append("Destino: ").append(destinoNombre).append("\n");
                logBuilder.append("  - Ubigeo: ").append(destinoUbigeo).append("\n");
                logBuilder.append("  - Nombre: ").append(destinoNombre).append("\n");
            } else {
                logBuilder.append("Destino: Ubigeo desconocido: ").append(destinoUbigeo).append("\n");
            }

            logBuilder.append("Tiempo Límite de Entrega: ").append(dueTimeStr).append("\n");
        }

        logBuilder.append("Tiempo de Partida: ").append(departureTimeStr).append("\n");
        logBuilder.append("Tiempo de Llegada: ").append(arrivalTimeStr).append("\n");
        logBuilder.append("Duración del Viaje: ").append(Duration.between(departureTime, currentTime).toMinutes()).append(" minutos\n");
        logBuilder.append("--------------------------");

        logger.info(logBuilder.toString());
    }

    private int calculateDeliverablePackages() {
        int remainingAssignedPackages = currentOrder.getRemainingPackagesToDeliver();
        return Math.min(this.getCapacity(), remainingAssignedPackages);
    }

    private void updateWarehouseCapacity(WarehouseManager warehouseManager, int deliverablePackages) {
        this.currentLocationUbigeo = currentOrder.getDestinationUbigeo();
        warehouseManager.decreaseCapacity(currentOrder.getDestinationUbigeo(), deliverablePackages);
    }

    private void updateOrderStatus(LocalDateTime currentTime, int deliverablePackages) {
        if (deliverablePackages > 0) {
            currentOrder.incrementDeliveredPackages(deliverablePackages);
            logger.info(String.format("Vehículo %s entrega %d paquetes de la orden %d.",
                    this.getCode(), deliverablePackages, currentOrder.getId()));
        }

        // Asegurarse de que no se exceda la cantidad total asignada
        if (currentOrder.getDeliveredPackages() > currentOrder.getAssignedPackages()) {
            currentOrder.setDeliveredPackages(currentOrder.getAssignedPackages());
            logger.warning(String.format("Vehículo %s ha intentado entregar más paquetes de los asignados para la orden %d. Ajustando a la cantidad asignada.",
                    this.getCode(), currentOrder.getId()));
        }

        // Actualizar el estado de la orden según la entrega
        if (currentOrder.isFullyDelivered()) {
            currentOrder.setStatus(Order.OrderStatus.PENDING_PICKUP);
            currentOrder.setPendingPickupStartTime(currentTime);
            logger.info(String.format("Orden %d completamente entregada y cambiada a estado PENDING_PICKUP a las %s",
                    currentOrder.getId(), currentTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        } else {
            currentOrder.setStatus(Order.OrderStatus.PARTIALLY_ARRIVED);
            logger.info(String.format("Orden %d parcialmente entregada. Faltan %d paquetes por entregar",
                    currentOrder.getId(), currentOrder.getRemainingPackagesToDeliver()));
        }
    }

    private void startWaitingPeriod(LocalDateTime currentTime) {
        waitStartTime = currentTime;
        logger.info(String.format("Vehículo %s comienza periodo de espera de 2 horas en %s",
                this.getCode(), this.currentLocationUbigeo));
    }

    private void resetVehicleStatus() {
        status = null;
        currentOrder = null;
        route = null;
        this.setListoParaRegresarAlmacen(false);

        if (estado == EstadoVehiculo.HACIA_ALMACEN) {
            estado = EstadoVehiculo.EN_ALMACEN;
        } else if (estado == EstadoVehiculo.EN_TRANSITO_ORDEN) {
            estado = EstadoVehiculo.EN_ESPERA_EN_OFICINA;
        }

        //this.setAvailable(true);
    }

    private void updateCurrentSegmentStatus(LocalDateTime currentTime) {
        long elapsedMinutes = ChronoUnit.MINUTES.between(status.getSegmentStartTime(), currentTime);
        logger.info(String.format("Vehículo %s - Velocidad: %.2f km/h, Tramo: %s (%s), Tiempo en tramo: %d minutos",
                this.getCode(), status.getCurrentSpeed(), status.getCurrentSegment(),
                status.getCurrentSegmentUbigeo(), elapsedMinutes));
    }

    private LocalDateTime calculateEstimatedArrivalTime(LocalDateTime startTime, List<RouteSegment> route) {
        long totalDurationMinutes = route.stream()
                .mapToLong(RouteSegment::getDurationMinutes)
                .sum();
        return startTime.plusMinutes(totalDurationMinutes);
    }

    /**
     * Inicia un viaje para el vehículo y registra detalles en los logs.
     * @param startTime        La hora de inicio del viaje.
     * @param order            La orden asignada al vehículo.
     */
    public void startJourney(LocalDateTime startTime, Order order) {
        if (this.route == null || this.route.isEmpty()) {
            logger.warning(String.format("Intento de iniciar un viaje para el vehículo %s con una ruta vacía.", this.getCode()));
            return;
        }

        this.currentSegmentIndex = 0;
        this.status = new VehicleStatus();
        this.currentOrder = order;
        this.departureTime = startTime;
        this.setAvailable(false);
        this.estado = EstadoVehiculo.EN_TRANSITO_ORDEN;

        updateCurrentSegment(startTime);

        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String estimatedArrivalStr = calculateEstimatedArrivalTime(startTime, this.route).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String dueTimeStr = order.getDueTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n--- Inicio de Viaje ---\n");
        logBuilder.append("Código del Vehículo: ").append(this.getCode()).append("\n");
        logBuilder.append("Capacidad del Vehículo: ").append(this.getCapacity()).append(" paquetes\n");
        logBuilder.append("Orden a Entregar:\n");
        logBuilder.append("  - Código de la Orden: ").append(order.getId()).append("\n");
        logBuilder.append("  - Cantidad Total de la Orden: ").append(order.getQuantity()).append(" paquetes\n");
        logBuilder.append("  - Cantidad Asignada al Vehículo: ").append(order.getRemainingPackagesToDeliver()).append(" paquetes\n");
        logBuilder.append("Origen: ").append(order.getOriginUbigeo()).append(" (").append(DataLoader.ubigeoToNameMap.getOrDefault(order.getOriginUbigeo(), "Desconocido")).append(")\n");
        logBuilder.append("Destino: ").append(order.getDestinationUbigeo()).append(" (").append(DataLoader.ubigeoToNameMap.getOrDefault(order.getDestinationUbigeo(), "Desconocido")).append(")\n");
        logBuilder.append("Tiempo de Inicio de Viaje: ").append(startTimeStr).append("\n");
        logBuilder.append("Tiempo Estimado de Llegada: ").append(estimatedArrivalStr).append("\n");
        logBuilder.append("Tiempo Límite de Entrega: ").append(dueTimeStr).append("\n");
        logBuilder.append("-------------------------");

        logger.info(logBuilder.toString());
    }

    public void startWarehouseJourney(LocalDateTime startTime, String destinationWarehouseUbigeo) {
        if (this.route == null || this.route.isEmpty()) {
            logger.warning(String.format("Intento de iniciar un viaje al almacén para el vehículo %s con una ruta vacía.", this.getCode()));
            return;
        }

        this.currentSegmentIndex = 0;
        this.status = new VehicleStatus();
        this.currentOrder = null; // No hay orden activa en este viaje
        this.setAvailable(false); // no disponible porque se dirige a almacen
        this.estado = EstadoVehiculo.HACIA_ALMACEN;
        this.departureTime = startTime;

        updateCurrentSegment(startTime);

        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String estimatedArrivalStr = calculateEstimatedArrivalTime(startTime, this.route).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("\n--- Inicio de Viaje al Almacén ---\n");
        logBuilder.append("Código del Vehículo: ").append(this.getCode()).append("\n");
        logBuilder.append("Capacidad del Vehículo: ").append(this.getCapacity()).append(" paquetes\n");
        logBuilder.append("Origen: ").append(this.getCurrentLocationUbigeo()).append(" (").append(DataLoader.ubigeoToNameMap.getOrDefault(this.getCurrentLocationUbigeo(), "Desconocido")).append(")\n");
        logBuilder.append("Destino (Almacén): ").append(destinationWarehouseUbigeo).append(" (").append(DataLoader.ubigeoToNameMap.getOrDefault(destinationWarehouseUbigeo, "Desconocido")).append(")\n");
        logBuilder.append("Tiempo de Inicio de Viaje: ").append(startTimeStr).append("\n");
        logBuilder.append("Tiempo Estimado de Llegada: ").append(estimatedArrivalStr).append("\n");
        logBuilder.append("-------------------------");

        logger.info(logBuilder.toString());
    }

    private void updateCurrentSegment(LocalDateTime currentTime) {
        RouteSegment segment = route.get(currentSegmentIndex);
        status.setCurrentSegment(segment.getName());
        status.setCurrentSegmentUbigeo(segment.getUbigeo());
        status.setSegmentStartTime(currentTime);
        status.setEstimatedArrivalTime(currentTime.plusMinutes(segment.getDurationMinutes()));
        status.setCurrentSpeed(segment.getDistance() / (segment.getDurationMinutes() / 60.0));
    }

    public List<RouteSegment> getRoute() {
        return this.route;
    }

    public boolean isRouteBeingCalculated() {
        return isRouteBeingCalculated;
    }

    public void setRouteBeingCalculated(boolean routeBeingCalculated) {
        isRouteBeingCalculated = routeBeingCalculated;
    }
}
