package com.odiparpack.models;

public class Edge {
    private String originUbigeo;
    private String destinationUbigeo;
    private double distance; // En kilómetros
    private double travelTime; // En horas
    public int orderId;

    // Constructor
    public Edge(String originUbigeo, String destinationUbigeo, double distance, double travelTime) {
        this.originUbigeo = originUbigeo;
        this.destinationUbigeo = destinationUbigeo;
        this.distance = distance;
        this.travelTime = travelTime;
    }

    // Getters
    public int getOrderId() {
        return orderId;
    }
    public String getOriginUbigeo() { return originUbigeo; }
    public String getDestinationUbigeo() { return destinationUbigeo; }
    public double getDistance() { return distance; }
    public double getTravelTime() { return travelTime; }
}
