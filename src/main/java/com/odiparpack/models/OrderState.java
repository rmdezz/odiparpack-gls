package com.odiparpack.models;

public class OrderState {
    private Order order;
    private boolean assigned;

    public OrderState(Order order, boolean assigned) {
        this.order = order;
        this.assigned = assigned;
    }

    public Order getOrder() {
        return order;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }
}
