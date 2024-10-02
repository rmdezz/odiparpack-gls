package com.odiparpack.models;

import java.time.LocalDateTime;

public class Blockage implements Cloneable {
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

    @Override
    public String toString() {
        return "Blockage{" +
                "from=" + originUbigeo +
                ", to=" + destinationUbigeo +
                "}";
    }

    // Getters
    public String getOriginUbigeo() { return originUbigeo; }
    public String getDestinationUbigeo() { return destinationUbigeo; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    // Implementación del método clone()
    @Override
    public Blockage clone() {
        try {
            return (Blockage) super.clone();
        } catch (CloneNotSupportedException e) {
            // Esto no debería ocurrir porque estamos implementando Cloneable
            throw new AssertionError();
        }
    }
}
