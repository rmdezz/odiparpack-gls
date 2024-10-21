package com.odiparpack.models;

import java.util.List;

public class VehicleRoute {
    private String vehicleCode;
    public List<RouteStep> steps;
    public int vehicleIndex;
    public long routeTime;

    public VehicleRoute(String vehicleCode, List<RouteStep> steps) {
        this.vehicleCode = vehicleCode;
        this.steps = steps;
    }

    public VehicleRoute() {

    }

    public String getVehicleCode() { return vehicleCode; }
    public List<RouteStep> getSteps() { return steps; }
}
