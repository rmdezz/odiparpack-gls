package com.odiparpack.models;

public class Location {
    private String ubigeo;
    private String department;
    private String province;
    private double latitude;
    private double longitude;
    private String naturalRegion;
    private int warehouseCapacity;

    // Constructor
    public Location(String ubigeo, String department, String province, double latitude, double longitude, String naturalRegion, int warehouseCapacity) {
        this.ubigeo = ubigeo;
        this.department = department;
        this.province = province;
        this.latitude = latitude;
        this.longitude = longitude;
        this.naturalRegion = naturalRegion;
        this.warehouseCapacity = warehouseCapacity;
    }

    // Getters y setters
    public String getUbigeo() {
        return ubigeo;
    }

    public void setUbigeo(String ubigeo) {
        this.ubigeo = ubigeo;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getNaturalRegion() {
        return naturalRegion;
    }

    public void setNaturalRegion(String naturalRegion) {
        this.naturalRegion = naturalRegion;
    }

    public int getWarehouseCapacity() {
        return warehouseCapacity;
    }

    public void setWarehouseCapacity(int warehouseCapacity) {
        this.warehouseCapacity = warehouseCapacity;
    }
}
