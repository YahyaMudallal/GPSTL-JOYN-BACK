package com.gpstl.joyn.common.geocoding;

public record Coordinates(double longitude, double latitude) {
    public Coordinates {
        if (!Double.isFinite(longitude) 
            || longitude < -180 
            || longitude > 180
            || !Double.isFinite(latitude) 
            || latitude < -90 
            || latitude > 90) {
            throw new IllegalArgumentException("Coordinates are outside valid longitude/latitude ranges");
        }
    }
}
