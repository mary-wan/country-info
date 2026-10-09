package com.example.countryinformation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryLookupRequest(
        @NotBlank(message = "Country name is required")
        @Size(max = 100, message = "Country name must not exceed 100 characters")
        @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Country name contains invalid characters")
        String name) {
}
