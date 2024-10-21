package com.odiparpack.models;

import com.odiparpack.DataLoader;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.odiparpack.DataLoader.getUbigeoFromName;
import static com.odiparpack.Main.locations;
import static com.odiparpack.Main.logger;
import static com.odiparpack.models.SimulationState.addBreakdownLog;

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
        EN_MANTENIMIENTO
    }

    private String code;
    private String type; // A, B, C
    private int capacity;
    private String currentLocationUbigeo;
    private boolean isAvailable;
    private String homeUbigeo;
    private VehicleStatus status;
    private boolean isRouteBeingCalculated;
    private LocalDateTime repairEndTime;
    private Order currentOrder;
    private List<RouteSegment> route;
    private int currentSegmentIndex; // Índice del tramo actual en la ruta
    private long elapsedTimeInSegment; // Tiempo transcurrido en el tramo actual (en minutos)
    LocalDateTime estimatedDeliveryTime;
    private long totalAveriaTime; // Tiempo total en estado de avería (en minutos)
    private LocalDateTime averiaStartTime; // Tiempo de inicio de la avería

    public long getTotalAveriaTime() {
        return totalAveriaTime;
    }

    public void setTotalAveriaTime(long totalAveriaTime) {
        this.totalAveriaTime = totalAveriaTime;
    }

    public LocalDateTime getAveriaStartTime() {
        return averiaStartTime;
    }

    public void setAveriaStartTime(LocalDateTime averiaStartTime) {
        this.averiaStartTime = averiaStartTime;
    }

    public boolean isInMaintenance() {
        return this.estado == EstadoVehiculo.EN_MANTENIMIENTO;
    }

    public boolean isUnderRepair() {
        return this.estado == EstadoVehiculo.AVERIADO_1 ||
                this.estado == EstadoVehiculo.AVERIADO_2 ||
                this.estado == EstadoVehiculo.AVERIADO_3;
    }

    public boolean hasCompletedRepair(LocalDateTime currentTime) {
        return this.repairEndTime != null && !currentTime.isBefore(this.repairEndTime);
    }

    /**
     * Inicia el seguimiento del tiempo de avería.
     */
    public void startAveria(LocalDateTime currentTime) {
        this.averiaStartTime = currentTime;
        this.totalAveriaTime = 0;
    }

    /**
     * Actualiza el tiempo total de avería basado en el tiempo actual.
     */
    public void updateAveriaTime(LocalDateTime currentTime) {
        if (this.averiaStartTime != null) {
            // Calcular el tiempo transcurrido desde el inicio de la avería hasta el currentTime
            long minutesSinceAveriaStart = ChronoUnit.MINUTES.between(this.averiaStartTime, currentTime);

            // Actualizar el tiempo total de avería
            this.totalAveriaTime += minutesSinceAveriaStart;

            // Registrar el tiempo transcurrido desde que comenzó la avería
            logger.info(String.format("Vehículo %s ha estado averiado durante %d minutos desde que ocurrió la avería inicialmente.",
                    this.getCode(), minutesSinceAveriaStart));
        }
    }

    /**
     * Finaliza el seguimiento del tiempo de avería.
     */
    public void endAveria() {
        this.averiaStartTime = null;
    }

    public void continueCurrentRoute(LocalDateTime currentTime) {
        this.estado = EstadoVehiculo.EN_TRANSITO_ORDEN;
        this.setAvailable(true);

        // Lógica para continuar la ruta actual
        if (this.route != null && this.currentSegmentIndex < this.route.size()) {
            RouteSegment currentSegment = this.route.get(this.currentSegmentIndex);
            long remainingTimeInSegment = currentSegment.getDurationMinutes() - this.elapsedTimeInSegment;

            // Actualizar el tiempo estimado de entrega sumando el tiempo de reparación (solo para avería tipo 1)
            if (this.estado == EstadoVehiculo.AVERIADO_1) {
                this.estimatedDeliveryTime = this.estimatedDeliveryTime.plusHours(4); // Ajustar según el tiempo de reparación
            }

            logger.info(String.format("Vehículo %s reanudará el tramo %d: %s. Tiempo restante en el tramo: %d minutos.",
                    this.getCode(),
                    this.currentSegmentIndex + 1,
                    currentSegment.getName(),
                    remainingTimeInSegment));

            // Actualizar el tiempo de fin del tramo
            this.status.setEstimatedArrivalTime(this.status.getEstimatedArrivalTime().plusHours(4));  // Agregar 4 horas adicionales

            // Finalizar el seguimiento del tiempo de avería
            this.endAveria();

            // Registrar la continuación de la ruta
            logger.info(String.format("Vehículo %s ha reanudado su ruta en el tramo %d: %s.",
                    this.getCode(),
                    this.currentSegmentIndex + 1,
                    currentSegment.getName()));
        } else {
            logger.warning(String.format("Vehículo %s no tiene una ruta válida para continuar.", this.getCode()));
        }
    }


    public void clearRepairTime() {
        this.repairEndTime = null;
    }


    public boolean shouldUpdateStatus() {
        return this.estado == EstadoVehiculo.EN_TRANSITO_ORDEN ||
                this.estado == EstadoVehiculo.HACIA_ALMACEN ||
                this.estado == EstadoVehiculo.EN_ESPERA_EN_OFICINA;
    }

    public boolean shouldCalculateNewRoute(LocalDateTime currentTime) {
        return this.estado == EstadoVehiculo.LISTO_PARA_RETORNO && !this.isRouteBeingCalculated() &&
                (this.getWaitStartTime() == null ||
                        ChronoUnit.MINUTES.between(this.getWaitStartTime(), currentTime) >= WAIT_TIME_MINUTES);
    }

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

    public LocalDateTime getRepairEndTime() {
        return repairEndTime;
    }

    public long getElapsedTimeInSegment() {
        return this.elapsedTimeInSegment;
    }

    public void setElapsedTimeInSegment(long elapsedTime) {
        this.elapsedTimeInSegment = elapsedTime;
    }

    public void setRepairEndTime(LocalDateTime repairEndTime) {
        this.repairEndTime = repairEndTime;
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

    public void handleBreakdown(LocalDateTime currentTime, EstadoVehiculo tipoAveria) {
        this.estado = tipoAveria;
        this.setAvailable(false);

        long repairHours;
        switch (tipoAveria) {
            case AVERIADO_1:
                repairHours = 4;
                break;
            case AVERIADO_2:
                repairHours = 36;
                break;
            case AVERIADO_3:
                repairHours = 72;
                break;
            default:
                logger.warning("Tipo de avería no reconocido");
                return;
        }

        // Calcular el tiempo de finalización de la reparación basado en el tiempo de simulación
        LocalDateTime repairEndTime = currentTime.plusHours(repairHours);
        this.setRepairEndTime(repairEndTime);

        // Registrar el tramo actual y el tiempo transcurrido hasta la avería
            if (this.route != null && this.currentSegmentIndex < this.route.size()) {
            RouteSegment currentSegment = this.route.get(this.currentSegmentIndex);
            long elapsedMinutes = ChronoUnit.MINUTES.between(this.status.getSegmentStartTime(), currentTime);
            this.setElapsedTimeInSegment(elapsedMinutes);

            String segmentLog = String.format("Vehículo %s ha sufrido una avería tipo %s en el tramo %d: %s. Tiempo transcurrido en el tramo: %d minutos.",
                    this.getCode(), tipoAveria, this.currentSegmentIndex + 1, currentSegment.getName(), elapsedMinutes);
            addBreakdownLog(this.getCode(), segmentLog);
            logger.info(segmentLog);
        }

        // Iniciar el seguimiento del tiempo de avería
        this.startAveria(currentTime);

        String breakdownLog = String.format("Vehículo %s ha sufrido una avería tipo %s en %s. Estará detenido hasta %s.",
                this.getCode(), tipoAveria, this.getCurrentLocationUbigeo(), repairEndTime);
        addBreakdownLog(this.getCode(), breakdownLog);
        logger.info(breakdownLog);
    }

    public Order getCurrentOrder() {
        return currentOrder;
    }

    public void setCurrentOrder(Order currentOrder) {
        this.currentOrder = currentOrder;
    }


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
    } // estimatedDeliveryTime

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

            // Obtener el ubigeo a partir del nombre de la ubicación
            currentLocationUbigeo = getUbigeoFromName(toName);

            if (currentLocationUbigeo != null) {
                logger.info(String.format("Ubicacion actual actualizada del vehiculo %s a: %s (%s).",
                        getCode(), currentLocationUbigeo, toName));
            } else {
                logger.warning(String.format("No se encontró el ubigeo para la ubicación: %s", toName));
            }
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
            logger.info(String.format("Estado del vehiculo %s actualizado a: EN ALMACEN - %s (%s)",
                    getCode(),
                    locations.get(getCurrentLocationUbigeo()).getProvince(),
                    getCurrentLocationUbigeo()));
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
        this.elapsedTimeInSegment = 0;
        this.status = new VehicleStatus();
        this.currentOrder = order;
        this.departureTime = startTime;
        this.setAvailable(false);
        this.estado = EstadoVehiculo.EN_TRANSITO_ORDEN;

        updateCurrentSegment(startTime);

        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        estimatedDeliveryTime = calculateEstimatedArrivalTime(startTime, this.route);
        String estimatedArrivalStr = estimatedDeliveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
