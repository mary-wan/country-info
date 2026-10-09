package com.example.countryinformation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "country-info.soap")
public record SoapProperties(
        @NotBlank String endpoint,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
