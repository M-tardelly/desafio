package com.example.activation.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuração do provedor externo ({@code app.provider}).
 *
 * @param baseUrl        URL base do provedor
 * @param connectTimeout tempo máximo para abrir a conexão
 * @param readTimeout    tempo máximo esperando a resposta
 * @param apiKey         credencial do provedor; vem de variável de ambiente / gerenciador de segredos,
 *                       nunca do repositório
 * @since 2026-10-29
 */
@Validated
@ConfigurationProperties(prefix = "app.provider")
public record ProviderProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        String apiKey) {
}
