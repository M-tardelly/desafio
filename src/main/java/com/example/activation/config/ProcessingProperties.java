package com.example.activation.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param processingLease tempo máximo em PROCESSING antes de a solicitação ser considerada presa
 *                        (ex.: a instância caiu durante a chamada ao provedor). Deve ser maior que o
 *                        timeout total da chamada ao provedor.
 */
@Validated
@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(
        boolean schedulerEnabled,
        @Min(1) int batchSize,
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        @NotNull Duration processingLease) {
}
