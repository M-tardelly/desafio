package com.example.activation;

import com.example.activation.application.ActivationProcessor;
import com.example.activation.domain.ActivationRequestRepository;
import com.example.activation.domain.ActivationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivationFlowIT {

    private static final String ACTIVATE = "/provider/v1/activate";

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("app.provider.base-url", provider::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ActivationProcessor processor;
    @Autowired
    private ActivationRequestRepository repository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void activatesRequestWhenProviderSucceeds() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(ok()));

        var created = create(systemA(), "EXT-OK", "CUST-1", "INTERNET-500");
        assertThat(created.status).isEqualTo("PENDING");

        assertThat(processor.processDueRequests()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/requests/{id}", created.id).with(systemA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.attempts").value(1));
        provider.verify(1, postRequestedFor(urlEqualTo(ACTIVATE))
                .withHeader("Idempotency-Key", equalTo(created.id.toString()))
                .withHeader("X-Correlation-Id", equalTo(created.correlationId))
                .withRequestBody(matchingJsonPath("$.customerId", equalTo("CUST-1")))
                .withRequestBody(matchingJsonPath("$.productCode", equalTo("INTERNET-500"))));
    }

    @Test
    void failsWithoutRetryWhenProviderRejects() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(aResponse().withStatus(400)));
        var created = create(systemA(), "EXT-REJECTED", "CUST-1", "UNKNOWN-PRODUCT");

        processor.processDueRequests();

        var request = repository.findById(created.id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getLastError()).contains("HTTP 400");
        assertThat(processor.processDueRequests()).isZero();
        provider.verify(1, postRequestedFor(urlEqualTo(ACTIVATE)));
    }

    @Test
    void retriesTemporaryFailureWithSameIdempotencyKeyUntilLimit() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(serverError()));
        var created = create(systemA(), "EXT-DOWN", "CUST-1", "INTERNET-500");

        processor.processDueRequests();
        var afterFirstAttempt = repository.findById(created.id).orElseThrow();
        assertThat(afterFirstAttempt.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(afterFirstAttempt.getAttempts()).isEqualTo(1);
        assertThat(afterFirstAttempt.getLastError()).contains("HTTP 500");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            processor.processDueRequests();
            assertThat(repository.findById(created.id).orElseThrow().getStatus()).isEqualTo(ActivationStatus.FAILED);
        });

        var failed = repository.findById(created.id).orElseThrow();
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getLastError()).contains("Limite de 3 tentativas");
        provider.verify(3, postRequestedFor(urlEqualTo(ACTIVATE))
                .withHeader("Idempotency-Key", equalTo(created.id.toString())));
    }

    @Test
    void completesAfterTransientFailure() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serviceUnavailable()).willSetStateTo("recovered"));
        provider.stubFor(WireMock.post(ACTIVATE).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(ok()));
        var created = create(systemA(), "EXT-FLAKY", "CUST-1", "INTERNET-500");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            processor.processDueRequests();
            assertThat(repository.findById(created.id).orElseThrow().getStatus())
                    .isEqualTo(ActivationStatus.COMPLETED);
        });
        assertThat(repository.findById(created.id).orElseThrow().getAttempts()).isEqualTo(2);
    }

    @Test
    void honorsRetryAfterHeader() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
        var created = create(systemA(), "EXT-THROTTLED", "CUST-1", "INTERNET-500");
        var before = Instant.now();

        processor.processDueRequests();

        var request = repository.findById(created.id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getNextAttemptAt()).isAfter(before.plusSeconds(29));
        assertThat(processor.processDueRequests()).isZero();
    }

    @Test
    void treatsTimeoutAsRetryable() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(ok().withFixedDelay(1_500)));
        var created = create(systemA(), "EXT-SLOW", "CUST-1", "INTERNET-500");

        processor.processDueRequests();

        var request = repository.findById(created.id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getLastError()).contains("Timeout");
    }

    @Test
    void reschedulesRequestsStuckInProcessing() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(ok()));
        var created = create(systemA(), "EXT-STUCK", "CUST-1", "INTERNET-500");
        simulateCrashAfterClaim(created.id);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(processor.recoverStuckRequests()).isEqualTo(1));
        assertThat(repository.findById(created.id).orElseThrow().getStatus()).isEqualTo(ActivationStatus.PENDING);

        processor.processDueRequests();
        assertThat(repository.findById(created.id).orElseThrow().getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void returnsExistingRequestWhenSameRequestIsRetried() throws Exception {
        var first = create(systemA(), "EXT-RETRY", "CUST-1", "INTERNET-500");

        var retry = mockMvc.perform(post("/api/v1/requests").with(systemA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-RETRY", "CUST-1", "INTERNET-500")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(read(retry).get("id").asText()).isEqualTo(first.id.toString());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsSameExternalIdWithDifferentContent() throws Exception {
        create(systemA(), "EXT-CONFLICT", "CUST-1", "INTERNET-500");

        mockMvc.perform(post("/api/v1/requests").with(systemA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-CONFLICT", "CUST-1", "INTERNET-1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void isolatesRequestsBetweenClients() throws Exception {
        var fromA = create(systemA(), "EXT-SHARED", "CUST-1", "INTERNET-500");

        mockMvc.perform(get("/api/v1/requests/{id}", fromA.id).with(systemB()))
                .andExpect(status().isNotFound());

        var fromB = create(systemB(), "EXT-SHARED", "CUST-9", "TV-PREMIUM");
        assertThat(fromB.id).isNotEqualTo(fromA.id);
    }

    @Test
    void returns404ForUnknownRequest() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()).with(systemA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listsRequestsWithFiltersAndPagination() throws Exception {
        provider.stubFor(WireMock.post(ACTIVATE).willReturn(ok()));
        create(systemA(), "EXT-L1", "CUST-1", "INTERNET-500");
        processor.processDueRequests();
        create(systemA(), "EXT-L2", "CUST-1", "TV-PREMIUM");
        create(systemA(), "EXT-L3", "CUST-2", "INTERNET-500");
        create(systemB(), "EXT-L4", "CUST-1", "INTERNET-500");

        mockMvc.perform(get("/api/v1/requests").param("customerId", "CUST-1").with(systemA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/requests").param("status", "PENDING").param("size", "1").with(systemA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    private void simulateCrashAfterClaim(UUID id) {
        transactionTemplate.executeWithoutResult(tx -> repository.findById(id).orElseThrow()
                .startProcessing(Instant.now()));
    }

    private Created create(RequestPostProcessor client, String externalId, String customerId, String productCode)
            throws Exception {
        var result = mockMvc.perform(post("/api/v1/requests").with(client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(externalId, customerId, productCode)))
                .andExpect(status().isCreated())
                .andReturn();
        var json = read(result);
        return new Created(UUID.fromString(json.get("id").asText()), json.get("status").asText(),
                result.getResponse().getHeader("X-Correlation-Id"));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String body(String externalId, String customerId, String productCode) {
        return """
                {"externalId": "%s", "customerId": "%s", "productCode": "%s"}
                """.formatted(externalId, customerId, productCode);
    }

    private static RequestPostProcessor systemA() {
        return client("system-a");
    }

    private static RequestPostProcessor systemB() {
        return client("system-b");
    }

    private static RequestPostProcessor client(String clientId) {
        return jwt().jwt(token -> token.subject(clientId))
                .authorities(new SimpleGrantedAuthority("SCOPE_activation:read"),
                        new SimpleGrantedAuthority("SCOPE_activation:write"));
    }

    private record Created(UUID id, String status, String correlationId) {
    }
}
