package com.example.activation.service.impl;

import com.example.activation.config.ProviderClientConfig;
import com.example.activation.config.ProviderProperties;
import com.example.activation.dto.ProviderResult;
import com.example.activation.mapper.ActivationRequestMapper;
import com.example.activation.model.ActivationRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderServiceImplTest {

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProviderServiceImpl service;
    private final ActivationRequest request =
            ActivationRequest.create("system-a", "EXT-1", "CUST-1", "INTERNET-500", "corr-123", Instant.now());

    @BeforeEach
    void setUp() {
        service = newService(provider.baseUrl(), Duration.ofMillis(500), "secret-key");
    }

    private static ProviderServiceImpl newService(String baseUrl, Duration timeout, String apiKey) {
        var properties = new ProviderProperties(baseUrl, timeout, timeout, apiKey);
        var restClient = new ProviderClientConfig().providerRestClient(RestClient.builder(), properties);
        return new ProviderServiceImpl(restClient, Mappers.getMapper(ActivationRequestMapper.class), Clock.systemUTC(),
                new SimpleMeterRegistry());
    }

    @Test
    void sendsIdempotencyKeyCorrelationIdAndCredentials() {
        provider.stubFor(post(ProviderServiceImpl.ACTIVATE_PATH).willReturn(ok()));

        var result = service.activate(request);

        assertThat(result).isInstanceOf(ProviderResult.Activated.class);
        provider.verify(postRequestedFor(urlEqualTo(ProviderServiceImpl.ACTIVATE_PATH))
                .withHeader("Idempotency-Key", equalTo(request.getId().toString()))
                .withHeader("X-Correlation-Id", equalTo("corr-123"))
                .withHeader("X-Api-Key", equalTo("secret-key"))
                .withRequestBody(equalToJson("""
                        {"requestId": "%s", "customerId": "CUST-1", "productCode": "INTERNET-500"}
                        """.formatted(request.getId()))));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 422})
    void clientErrorsAreFinalRejections(int status) {
        provider.stubFor(post(ProviderServiceImpl.ACTIVATE_PATH).willReturn(aResponse().withStatus(status)));

        var result = service.activate(request);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.Rejected.class,
                rejected -> assertThat(rejected.reason()).contains(String.valueOf(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408, 429})
    void serverErrorsAndThrottlingAreRetryable(int status) {
        provider.stubFor(post(ProviderServiceImpl.ACTIVATE_PATH).willReturn(aResponse().withStatus(status)));

        var result = service.activate(request);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.retryAfter()).isNull());
    }

    @Test
    void readsRetryAfterInSeconds() {
        provider.stubFor(post(ProviderServiceImpl.ACTIVATE_PATH)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));

        var result = service.activate(request);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void timeoutIsRetryableBecauseOutcomeIsUnknown() {
        provider.stubFor(post(ProviderServiceImpl.ACTIVATE_PATH).willReturn(ok().withFixedDelay(1_500)));

        var result = service.activate(request);

        assertThat(result).isInstanceOfSatisfying(ProviderResult.TemporaryFailure.class,
                failure -> assertThat(failure.reason()).contains("Timeout"));
    }

    @Test
    void connectionFailureIsRetryable() {
        var unreachable = newService("http://localhost:1", Duration.ofMillis(300), null);

        var result = unreachable.activate(request);

        assertThat(result).isInstanceOf(ProviderResult.TemporaryFailure.class);
    }
}
