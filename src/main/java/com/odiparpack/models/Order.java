package com.odiparpack.models;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.odiparpack.Main.logger;

public class Order {
    public enum OrderStatus {
        REGISTERED,
        IN_TRANSIT,
        FULLY_ASSIGNED,
        PARTIALLY_ASSIGNED,
        PENDING_PICKUP,
        PARTIALLY_ARRIVED,
        DELIVERED
    }

    private int id;
    private String originUbigeo;
    private String destinationUbigeo;
    private int quantity;
    private LocalDateTime orderTime;
    private LocalDateTime dueTime;
    private String clientId;
    private boolean assigned;
    private OrderStatus status;
    private int assignedPackages;
    private int deliveredPackages;

    public LocalDateTime getPendingPickupStartTime() {
        return pendingPickupStartTime;
    }

    public void setPendingPickupStartTime(LocalDateTime pendingPickupStartTime) {
        this.pendingPickupStartTime = pendingPickupStartTime;
    }

    private LocalDateTime pendingPickupStartTime;
    private static final Duration PICKUP_DURATION = Duration.ofHours(4);

    // Constructor
    public Order(int id, String originUbigeo, String destinationUbigeo, int quantity,
                 LocalDateTime orderTime, LocalDateTime dueTime, String clientId) {
        this.id = id;
        this.originUbigeo = originUbigeo;
        this.destinationUbigeo = destinationUbigeo;
        this.quantity = quantity;
        this.orderTime = orderTime;
        this.dueTime = dueTime;
        this.clientId = clientId;
        this.status = OrderStatus.REGISTERED;
    }

    // Getters y Setters
    public int getId() { return id; }
    public String getOriginUbigeo() { return originUbigeo; }
    public String getDestinationUbigeo() { return destinationUbigeo; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getOrderTime() { return orderTime; }
    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
    public String getClientId() { return clientId; }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    public int getAssignedPackages() {
        return assignedPackages;
    }

    public void incrementAssignedPackages(int count) {
        this.assignedPackages += count;
        updateStatus();
    }

    public int getDeliveredPackages() {
        return deliveredPackages;
    }

    public void incrementDeliveredPackages(int count) {
        this.deliveredPackages += count;
        if (this.deliveredPackages > this.assignedPackages) {
            this.deliveredPackages = this.assignedPackages;
            logger.warning(String.format("Orden %d: deliveredPackages excede assignedPackages. Ajustado a %d.",
                    this.id, this.deliveredPackages));
        }
        updateStatus();
    }

    public void setDeliveredPackages(int deliveredPackages) {
        if (deliveredPackages > this.assignedPackages) {
            this.deliveredPackages = this.assignedPackages;
            logger.warning(String.format("Orden %d: deliveredPackages excede assignedPackages. Ajustado a %d.",
                    this.id, this.deliveredPackages));
        } else {
            this.deliveredPackages = deliveredPackages;
        }
        updateStatus();
    }

    public void setAssignedPackages(int packages) {
        this.assignedPackages = packages;
    }

    // Método para obtener los paquetes no asignados
    public int getUnassignedPackages() {
        return quantity - assignedPackages;
    }

    // Método para obtener los paquetes restantes por entregar
    public int getRemainingPackagesToDeliver() {
        return assignedPackages - deliveredPackages;
    }

    // Método para verificar si la orden está completamente entregada
    public boolean isFullyDelivered() {
        return deliveredPackages == quantity;
    }

    // Método para actualizar el estado de la orden
    public void updateStatus() {
        OrderStatus oldStatus = this.status;
        // Estado basado en assignedPackages y quantity
        if (assignedPackages == 0) {
            this.status = OrderStatus.REGISTERED;
        } else if (assignedPackages < quantity) {
            this.status = OrderStatus.PARTIALLY_ASSIGNED;
        } else if (assignedPackages == quantity) {
            if (deliveredPackages == 0) {
                this.status = OrderStatus.FULLY_ASSIGNED;
            } else if (deliveredPackages < assignedPackages) {
                this.status = OrderStatus.PARTIALLY_ARRIVED;
            } else if (deliveredPackages == assignedPackages) {
                this.status = OrderStatus.PENDING_PICKUP;
            }
        }

        // Estado final basado en la cantidad total
        if (deliveredPackages == quantity) {
            this.status = OrderStatus.PENDING_PICKUP;
        }

        logger.info("Orden " + getId() + " actualizada a estado: " + this.status);
    }

    public boolean isReadyForDelivery(LocalDateTime currentTime) {
        return this.status == OrderStatus.PENDING_PICKUP &&
                currentTime.isAfter(this.pendingPickupStartTime.plus(PICKUP_DURATION));
    }

    public void setDelivered(LocalDateTime currentSimulationTime) {
        this.status = OrderStatus.DELIVERED;
        logger.info("Orden " + getId() + " marcada como DELIVERED a las " + currentSimulationTime);
    }
}
