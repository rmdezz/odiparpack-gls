package com.odiparpack.models;

// Clase para el segmento de la ruta
public class RouteSegment {
    private String name;
    private String fromUbigeo;
    private String toUbigeo;
    private String ubigeo;
    private double distance; // km
    private long durationMinutes;

    // Constructor
    public RouteSegment(String name, String fromUbigeo, String toUbigeo, double distance, long durationMinutes) {
        this.name = name;
        this.fromUbigeo = fromUbigeo;
        this.toUbigeo = toUbigeo;
        this.distance = distance;
        this.durationMinutes = durationMinutes;
    }

    // Getters y Setters
    public String getName() {
        return name;
    }

    public String getFromUbigeo() { return fromUbigeo; }
    public String getToUbigeo() { return toUbigeo; }

    public void setName(String name) {
        this.name = name;
    }

    public String getUbigeo() {
        return ubigeo;
    }

    public void setUbigeo(String ubigeo) {
        this.ubigeo = ubigeo;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}