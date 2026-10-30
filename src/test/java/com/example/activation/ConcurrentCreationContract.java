package com.example.activation;

import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.command.CreateActivationResult;
import com.example.activation.service.ActivationRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dispara várias criações simultâneas com o mesmo externalId contra um banco real.
 * Executado em H2 e em PostgreSQL (Testcontainers).
 *
 * @since 2026-10-29
 */
abstract class ConcurrentCreationContract {

    private static final int CONCURRENT_REQUESTS = 16;

    @Autowired
    private ActivationRequestService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicatesCreateExactlyOneRequest() throws Exception {
        String externalId = "EXT-" + UUID.randomUUID();
        CreateActivationCommand command = new CreateActivationCommand("concurrency-client", externalId, "CUST-1", "INTERNET-500", "corr");
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<CreateActivationResult> createAfterSignal = () -> {
            startSignal.await();
            return service.create(command);
        };

        List<CreateActivationResult> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<CreateActivationResult>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(executor.submit(createAfterSignal));
            }
            startSignal.countDown();
            for (Future<CreateActivationResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(results).filteredOn(CreateActivationResult::created).hasSize(1);
        UUID winnerId = results.stream().filter(CreateActivationResult::created).findFirst().orElseThrow().request().getId();
        assertThat(results).allSatisfy(result -> assertThat(result.request().getId()).isEqualTo(winnerId));
        assertThat(countRows(externalId)).isEqualTo(1);
    }

    private long countRows(String externalId) {
        List<Long> counts = jdbcTemplate.queryForList(
                "select count(*) from activation_request where external_id = ?", Long.class, externalId);
        return counts.getFirst();
    }
}
