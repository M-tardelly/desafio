package com.example.activation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Também valida as migrations Flyway e o mapeamento JPA ({@code ddl-auto=validate}) no PostgreSQL.
 * Ignorado automaticamente quando não há Docker disponível.
 *
 * @since 2026-10-29
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresConcurrentCreationIT extends ConcurrentCreationContract {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
