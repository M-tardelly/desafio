package com.example.activation;

import com.example.activation.service.ActivationRequestService;
import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.repository.ActivationRequestRepository;
import com.example.activation.model.ActivationStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        // banco próprio: este contexto fica em cache com o scheduler ativo e não pode processar dados de outros testes
        "spring.datasource.url=jdbc:h2:mem:scheduler-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.processing.scheduler-enabled=true",
        "app.processing.poll-interval=PT0.1S",
        "app.processing.processing-lease=PT1M"
})
@ActiveProfiles("test")
class ActivationSchedulerIT {

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("app.provider.base-url", provider::baseUrl);
    }

    @Autowired
    private ActivationRequestService service;
    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void processesNewRequestsInBackground() {
        provider.stubFor(post("/provider/v1/activate").willReturn(ok()));

        var created = service.create(
                new CreateActivationCommand("scheduler-client", "EXT-SCHEDULED", "CUST-1", "INTERNET-500", "corr"));
        assertThat(created.request().getStatus()).isEqualTo(ActivationStatus.PENDING);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(created.request().getId()).orElseThrow().getStatus())
                        .isEqualTo(ActivationStatus.COMPLETED));
    }
}
