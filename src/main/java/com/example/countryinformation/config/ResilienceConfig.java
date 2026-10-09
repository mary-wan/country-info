package com.example.countryinformation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.countryinformation.exception.UnknownCountryException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
@Slf4j
public class ResilienceConfig {

    public static final String COUNTRY_INFO = "countryInfo";

    @Bean
    public RetryRegistry retryRegistry(ResilienceProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        properties.backoff(), properties.backoffMultiplier()))
                .ignoreExceptions(UnknownCountryException.class)
                .build();
        return RetryRegistry.of(config);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(UnknownCountryException.class)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public Retry countryInfoRetry(RetryRegistry registry) {
        Retry retry = registry.retry(COUNTRY_INFO);
        retry.getEventPublisher().onRetry(event -> log.warn("Retrying SOAP call, attempt {} after {}",
                event.getNumberOfRetryAttempts(), event.getWaitInterval()));
        return retry;
    }

    @Bean
    public CircuitBreaker countryInfoCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker(COUNTRY_INFO);
        circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "Circuit breaker '{}' changed state {}", event.getCircuitBreakerName(), event.getStateTransition()));
        return circuitBreaker;
    }

    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry registry, MeterRegistry meterRegistry) {
        TaggedRetryMetrics metrics = TaggedRetryMetrics.ofRetryRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
            MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}
