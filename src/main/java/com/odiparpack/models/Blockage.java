package com.odiparpack.models;

import java.time.LocalDateTime;

public class Blockage {
    private String originUbigeo;
    private String destinationUbigeo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Constructor
    public Blockage(String originUbigeo, String destinationUbigeo, LocalDateTime startTime, LocalDateTime endTime) {
        this.originUbigeo = originUbigeo;
        this.destinationUbigeo = destinationUbigeo;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters
    public String getOriginUbigeo() { return originUbigeo; }
    public String getDestinationUbigeo() { return destinationUbigeo; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}
