package com.example.activation.infrastructure.provider;

import com.example.activation.application.provider.ActivationCommand;
import com.example.activation.application.provider.ProviderClient;
import com.example.activation.application.provider.ProviderResult;
import com.example.activation.application.provider.ProviderResult.Activated;
import com.example.activation.application.provider.ProviderResult.Rejected;
import com.example.activation.application.provider.ProviderResult.TemporaryFailure;
import com.example.activation.observability.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class HttpProviderClient implements ProviderClient {

    static final String ACTIVATE_PATH = "/provider/v1/activate";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String API_KEY_HEADER = "X-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(HttpProviderClient.class);

    private final RestClient restClient;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public HttpProviderClient(RestClient.Builder builder, ProviderProperties properties, Clock clock,
                              MeterRegistry meterRegistry) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (StringUtils.hasText(properties.apiKey())) {
            builder.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        this.restClient = builder.build();
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ProviderResult activate(ActivationCommand command) {
        var sample = Timer.start(meterRegistry);
        var result = send(command);
        sample.stop(meterRegistry.timer("provider.activation", "outcome", outcomeTag(result)));
        return result;
    }

    private ProviderResult send(ActivationCommand command) {
        try {
            return restClient.post()
                    .uri(ACTIVATE_PATH)
                    .headers(headers -> {
                        headers.set(IDEMPOTENCY_KEY_HEADER, command.requestId().toString());
                        if (command.correlationId() != null) {
                            headers.set(CorrelationId.HEADER, command.correlationId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ProviderActivationRequest(command.requestId(), command.customerId(),
                            command.productCode()))
                    .exchange((request, response) -> toResult(response.getStatusCode(), response.getHeaders()));
        } catch (ResourceAccessException e) {
            log.warn("Provider call for request {} failed: {}", command.requestId(), e.getMessage());
            return new TemporaryFailure(describeIoFailure(e), null);
        } catch (RestClientException e) {
            log.warn("Provider call for request {} failed", command.requestId(), e);
            return new TemporaryFailure("Erro na comunicação com o provedor", null);
        }
    }

    private ProviderResult toResult(HttpStatusCode status, HttpHeaders headers) {
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
        var cause = NestedExceptionUtils.getMostSpecificCause(e);
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
            var retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            var delay = Duration.between(clock.instant(), retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String outcomeTag(ProviderResult result) {
        return switch (result) {
            case Activated activated -> "activated";
            case Rejected rejected -> "rejected";
            case TemporaryFailure failure -> "temporary_failure";
        };
    }
}
