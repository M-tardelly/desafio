package com.example.activation.infrastructure.provider;

import com.example.activation.application.provider.ActivationCommand;
import com.example.activation.application.provider.ProviderResult;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpProviderClientTest {

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private HttpProviderClient client;
    private final ActivationCommand command =
            new ActivationCommand(UUID.randomUUID(), "CUST-1", "INTERNET-500", "corr-123");

    @BeforeEach
    void setUp() {
        var properties = new ProviderProperties(provider.baseUrl(), Duration.ofMillis(500), Duration.ofMillis(500),
                "secret-key");
        client = new HttpProviderClient(RestClient.builder(), properties, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test
    void sendsIdempotencyKeyCorrelationIdAndCredentials() {
        provider.stubFor(post(HttpProviderClient.ACTIVATE_PATH).willReturn(ok()));

        var result = client.activate(command);

        assertThat(result).isInstanceOf(ProviderResult.Activated.class);
        provider.verify(postRequestedFor(urlEqualTo(HttpProviderClient.ACTIVATE_PATH))
                .withHeader("Idempotency-Key", equalTo(command.requestId().toString()))
                .withHeader("X-Correlation-Id", equalTo("corr-123"))
                .withHeader("X-Api-Key", equalTo("secret-key"))
                .withRequestBody(equalToJson("""
                        {"requestId": "%s", "customerId": "CUST-1", "productCode": "INTERNET-500"}
                        """.formatted(command.requestId()))));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 422})
    void clientErrorsAreFinalRejections(int status) {
        provider.stubFor(post(HttpProviderClient.ACTIVATE_PATH).willReturn(aResponse().withStatus(status)));

        var result = client.activate(command);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.Rejected.class,
                rejected -> assertThat(rejected.reason()).contains(String.valueOf(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408, 429})
    void serverErrorsAndThrottlingAreRetryable(int status) {
        provider.stubFor(post(HttpProviderClient.ACTIVATE_PATH).willReturn(aResponse().withStatus(status)));

        var result = client.activate(command);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.retryAfter()).isNull());
    }

    @Test
    void readsRetryAfterInSeconds() {
        provider.stubFor(post(HttpProviderClient.ACTIVATE_PATH)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));

        var result = client.activate(command);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void timeoutIsRetryableBecauseOutcomeIsUnknown() {
        provider.stubFor(post(HttpProviderClient.ACTIVATE_PATH).willReturn(ok().withFixedDelay(1_500)));

        var result = client.activate(command);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.reason()).contains("Timeout"));
    }

    @Test
    void connectionFailureIsRetryable() {
        var properties = new ProviderProperties("http://localhost:1", Duration.ofMillis(300), Duration.ofMillis(300),
                null);
        var unreachable = new HttpProviderClient(RestClient.builder(), properties, Clock.systemUTC(),
                new SimpleMeterRegistry());

        var result = unreachable.activate(command);

        assertThat(result).isInstanceOf(ProviderResult.TemporaryFailure.class);
    }
}
