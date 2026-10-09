package com.example.countryinformation.exception;

public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(Long id) {
        super("No country found with id " + id);
    }
}
