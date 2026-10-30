package com.example.activation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Criação concorrente com o mesmo externalId contra o H2.
 *
 * @since 2026-10-29
 */
@SpringBootTest
@ActiveProfiles("test")
class H2ConcurrentCreationIT extends ConcurrentCreationContract {
}
