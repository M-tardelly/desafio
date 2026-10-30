package com.example.activation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do serviço de ativação de produtos. Habilita o agendamento usado no processamento
 * assíncrono e o registro das propriedades {@code app.*}.
 *
 * @since 2026-10-29
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ActivationServiceApplication {

    /**
     * Inicia a aplicação.
     *
     * @param args argumentos de linha de comando repassados ao Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ActivationServiceApplication.class, args);
    }
}
