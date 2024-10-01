package com.odiparpack.models;

public class Maintenance {
    private String vehicleCode;
    private long startTime; // Timestamp
    private long endTime;   // Timestamp

    // Constructor
    public Maintenance(String vehicleCode, long startTime, long endTime) {
        this.vehicleCode = vehicleCode;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters
    public String getVehicleCode() { return vehicleCode; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
}
