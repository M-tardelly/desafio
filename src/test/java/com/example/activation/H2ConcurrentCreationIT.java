package com.example.activation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class H2ConcurrentCreationIT extends ConcurrentCreationContract {
}
