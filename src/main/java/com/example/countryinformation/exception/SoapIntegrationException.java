package com.example.countryinformation.exception;

public class SoapIntegrationException extends RuntimeException {

    public SoapIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SoapIntegrationException(String message) {
        super(message);
    }
}
