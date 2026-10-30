package com.example.activation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * UTC com precisão de milissegundos: o banco guarda menos casas que o {@code Instant} do JDK, e assim o
     * valor devolvido pela API na criação é o mesmo que será lido do banco depois.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
