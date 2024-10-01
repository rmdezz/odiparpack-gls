package com.odiparpack.models;

public class VehicleState {
    private Vehicle vehicle;
    private String currentUbigeo;
    private long availableTime; // Tiempo en que el vehículo está disponible para nuevas asignaciones
    private boolean inTransit;
    private String homeUbigeo;

    // Constructor
    public VehicleState(Vehicle vehicle, String currentUbigeo, long availableTime, boolean inTransit, String homeUbigeo) {
        this.vehicle = vehicle;
        this.currentUbigeo = currentUbigeo;
        this.availableTime = availableTime;
        this.inTransit = inTransit;
        this.homeUbigeo = homeUbigeo;
    }

    // Getters y Setters
    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public String getCurrentUbigeo() {
        return currentUbigeo;
    }

    public void setCurrentUbigeo(String currentUbigeo) {
        this.currentUbigeo = currentUbigeo;
    }

    public long getAvailableTime() {
        return availableTime;
    }

    public void setAvailableTime(long availableTime) {
        this.availableTime = availableTime;
    }

    public boolean isInTransit() {
        return inTransit;
    }

    public void setInTransit(boolean inTransit) {
        this.inTransit = inTransit;
    }

    public String getHomeUbigeo() {
        return homeUbigeo;
    }

    public void setHomeUbigeo(String homeUbigeo) {
        this.homeUbigeo = homeUbigeo;
    }
}
