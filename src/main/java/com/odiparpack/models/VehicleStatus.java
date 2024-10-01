package com.odiparpack.models;

import java.time.LocalDateTime;

public class VehicleStatus {
    private String currentSegment;
    private String currentSegmentUbigeo;
    private LocalDateTime segmentStartTime;
    private LocalDateTime estimatedArrivalTime;
    private double currentSpeed; // km/h

    public VehicleStatus() {}

    // Getters y Setters
    public String getCurrentSegment() {
        return currentSegment;
    }

    public void setCurrentSegment(String currentSegment) {
        this.currentSegment = currentSegment;
    }

    public String getCurrentSegmentUbigeo() {
        return currentSegmentUbigeo;
    }

    public void setCurrentSegmentUbigeo(String currentSegmentUbigeo) {
        this.currentSegmentUbigeo = currentSegmentUbigeo;
    }

    public LocalDateTime getSegmentStartTime() {
        return segmentStartTime;
    }

    public void setSegmentStartTime(LocalDateTime segmentStartTime) {
        this.segmentStartTime = segmentStartTime;
    }

    public LocalDateTime getEstimatedArrivalTime() {
        return estimatedArrivalTime;
    }

    public void setEstimatedArrivalTime(LocalDateTime estimatedArrivalTime) {
        this.estimatedArrivalTime = estimatedArrivalTime;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = currentSpeed;
    }
}