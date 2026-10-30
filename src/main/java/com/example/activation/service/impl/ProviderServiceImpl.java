package com.example.activation.service.impl;

import com.example.activation.dto.provider.ProviderActivationResult.Activated;
import com.example.activation.dto.provider.ProviderActivationResult.Rejected;
import com.example.activation.dto.provider.ProviderActivationResult.TemporaryFailure;
import com.example.activation.dto.provider.ProviderActivationResult;
import com.example.activation.mapper.ActivationRequestMapper;
import com.example.activation.model.ActivationRequest;
import com.example.activation.service.ProviderService;
import com.example.activation.utils.CorrelationIdUtils;
import com.example.activation.utils.LogBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Cliente HTTP do provedor ({@code POST /provider/v1/activate}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    static final String ACTIVATE_PATH = "/provider/v1/activate";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * Configurado em {@link com.example.activation.config.ProviderClientConfig} (URL, timeouts e credencial).
     */
    private final RestClient providerRestClient;
    private final ActivationRequestMapper mapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Override
    public ProviderActivationResult activate(ActivationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        ProviderActivationResult result = send(request);
        sample.stop(meterRegistry.timer("provider.activation", "outcome", outcomeTag(result)));
        return result;
    }

    private ProviderActivationResult send(ActivationRequest request) {
        try {
            return providerRestClient.post()
                    .uri(ACTIVATE_PATH)
                    .headers(headers -> {
                        // o id da solicitação é a chave de idempotência: o mesmo valor em todas as tentativas
                        headers.set(IDEMPOTENCY_KEY_HEADER, request.getId().toString());
                        if (request.getCorrelationId() != null) {
                            headers.set(CorrelationIdUtils.HEADER, request.getCorrelationId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.toProviderRequest(request))
                    .exchange((httpRequest, response) -> toResult(response.getStatusCode(), response.getHeaders()));
        } catch (ResourceAccessException e) {
            LogBuilder.warn(log, "Provider call failed")
                    .with("requestId", request.getId())
                    .with("reason", e.getMessage())
                    .log();
            return new TemporaryFailure(describeIoFailure(e), null);
        } catch (RestClientException e) {
            LogBuilder.warn(log, "Provider call failed")
                    .with("requestId", request.getId())
                    .exception(e)
                    .log();
            return new TemporaryFailure("Erro na comunicação com o provedor", null);
        }
    }

    private ProviderActivationResult toResult(HttpStatusCode status, HttpHeaders headers) {
        if (status.is2xxSuccessful()) {
            return new Activated();
        }
        if (isRetryable(status)) {
            return new TemporaryFailure("Provedor indisponível (HTTP %d)".formatted(status.value()),
                    parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)));
        }
        if (status.is4xxClientError()) {
            return new Rejected("Provedor recusou a solicitação (HTTP %d)".formatted(status.value()));
        }
        return new TemporaryFailure("Resposta inesperada do provedor (HTTP %d)".formatted(status.value()), null);
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 408 || status.value() == 429;
    }

    private static String describeIoFailure(ResourceAccessException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
            return "Timeout na chamada ao provedor (resultado desconhecido)";
        }
        return "Falha de conexão com o provedor";
    }

    private Duration parseRetryAfter(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            // Retry-After também pode ser uma data HTTP
        }
        try {
            Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(clock.instant(), retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String outcomeTag(ProviderActivationResult result) {
        return switch (result) {
            case Activated activated -> "activated";
            case Rejected rejected -> "rejected";
            case TemporaryFailure failure -> "temporary_failure";
        };
    }
}
