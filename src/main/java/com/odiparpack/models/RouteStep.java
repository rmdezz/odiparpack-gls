package com.odiparpack.models;

public class RouteStep {
    private String ubigeo;
    private double latitude;
    private double longitude;
    private long arrivalTime;
    private long departureTime;

    public RouteStep(String ubigeo, double latitude, double longitude, long arrivalTime, long departureTime) {
        this.ubigeo = ubigeo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }


    public String getUbigeo() { return ubigeo; }
    public long getArrivalTime() { return arrivalTime; }
    public long getDepartureTime() { return departureTime; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}