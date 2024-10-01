package com.odiparpack.models;

public class VehicleAssignment {
    private Vehicle vehicle;
    private Order order;
    private int assignedQuantity;

    public VehicleAssignment(Vehicle vehicle, Order order, int assignedQuantity) {
        this.vehicle = vehicle;
        this.order = order;
        this.assignedQuantity = assignedQuantity;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Order getOrder() {
        return order;
    }

    public int getAssignedQuantity() {
        return assignedQuantity;
    }
}