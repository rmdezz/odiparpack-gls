package com.odiparpack.models;
import java.time.LocalDateTime;

public class Maintenance {
    private String vehicleCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Maintenance(String vehicleCode, LocalDateTime startTime, LocalDateTime endTime) {
        this.vehicleCode = vehicleCode;
        this.startTime = startTime;
        this.endTime = endTime;  // Ajuste para la Nota 1
    }

    public String getVehicleCode() {
        return vehicleCode;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isInMaintenancePeriod(LocalDateTime currentTime) {
        return !currentTime.isBefore(startTime) && currentTime.isBefore(endTime);
    }
}