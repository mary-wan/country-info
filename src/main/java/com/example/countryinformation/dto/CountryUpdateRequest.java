package com.example.countryinformation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CountryUpdateRequest(
        @NotBlank(message = "Country name is required")
        @Size(max = 100, message = "Country name must not exceed 100 characters")
        String name,

        @Size(max = 150, message = "Capital city must not exceed 150 characters")
        String capitalCity,

        @Size(max = 20, message = "Phone code must not exceed 20 characters")
        String phoneCode,

        @Size(max = 10, message = "Continent code must not exceed 10 characters")
        String continentCode,

        @Size(max = 10, message = "Currency ISO code must not exceed 10 characters")
        String currencyIsoCode,

        @Size(max = 500, message = "Flag URL must not exceed 500 characters")
        String flagUrl,

        List<@Valid LanguageRequest> languages) {
}
