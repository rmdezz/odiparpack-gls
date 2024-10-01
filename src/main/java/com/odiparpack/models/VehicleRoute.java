package com.odiparpack.models;

import java.util.List;

public class VehicleRoute {
    private String vehicleCode;
    private List<RouteStep> steps;

    public VehicleRoute(String vehicleCode, List<RouteStep> steps) {
        this.vehicleCode = vehicleCode;
        this.steps = steps;
    }

    public String getVehicleCode() { return vehicleCode; }
    public List<RouteStep> getSteps() { return steps; }
}
