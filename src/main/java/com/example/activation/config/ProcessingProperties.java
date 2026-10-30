package com.example.activation.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuração do processamento assíncrono ({@code app.processing}).
 *
 * @param schedulerEnabled liga o agendador nesta instância
 * @param batchSize        máximo de solicitações por ciclo
 * @param maxAttempts      chamadas ao provedor antes de marcar como FAILED
 * @param initialBackoff   espera antes da segunda tentativa; dobra a cada falha
 * @param maxBackoff       teto da espera entre tentativas (também limita o Retry-After)
 * @param processingLease  tempo máximo em PROCESSING antes de a solicitação ser considerada presa
 *                         (ex.: a instância caiu durante a chamada ao provedor). Deve ser maior que o
 *                         timeout total da chamada ao provedor.
 * @since 2026-10-29
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
