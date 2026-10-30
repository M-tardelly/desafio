package com.example.activation.infrastructure.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param apiKey credencial do provedor; vem de variável de ambiente / gerenciador de segredos, nunca do repositório.
 */
@Validated
@ConfigurationProperties(prefix = "app.provider")
public record ProviderProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        String apiKey) {
}
