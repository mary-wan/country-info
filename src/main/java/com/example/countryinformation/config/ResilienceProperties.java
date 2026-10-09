package com.example.countryinformation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "country-info.resilience")
public record ResilienceProperties(
        @Positive int maxAttempts,
        @NotNull Duration backoff,
        @Positive double backoffMultiplier,
        @Positive float failureRateThreshold,
        @NotNull Duration waitDurationInOpenState,
        @Positive int slidingWindowSize,
        @Positive int minimumNumberOfCalls,
        @Positive int permittedCallsInHalfOpenState) {
}
