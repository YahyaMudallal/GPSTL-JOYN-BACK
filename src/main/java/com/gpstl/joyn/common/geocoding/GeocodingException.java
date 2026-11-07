package com.gpstl.joyn.common.geocoding;

public class GeocodingException extends RuntimeException {
    public GeocodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public GeocodingException(String message) {
        super(message);
    }
}
