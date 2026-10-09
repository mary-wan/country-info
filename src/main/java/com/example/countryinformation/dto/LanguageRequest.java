package com.example.countryinformation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LanguageRequest(
        @Size(max = 10, message = "Language ISO code must not exceed 10 characters")
        String isoCode,

        @NotBlank(message = "Language name is required")
        @Size(max = 150, message = "Language name must not exceed 150 characters")
        String name) {
}
