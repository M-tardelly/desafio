package com.example.activation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI exposta em {@code /v3/api-docs} e Swagger UI em {@code /swagger-ui.html}.
 * <p>
 * Todas as operações exigem um JWT ({@code Authorization: Bearer ...}); no Swagger UI o token é informado
 * pelo botão "Authorize".
 *
 * @since 2026-10-29
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    static {
        // o sistema consumidor vem do token, não é um parâmetro que o cliente envia
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentClientId.class);
    }

    /**
     * Metadados da API e esquema de segurança Bearer JWT aplicado a todas as operações.
     *
     * @return definição OpenAPI usada pelo springdoc
     */
    @Bean
    public OpenAPI activationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Activation Service API")
                        .version("v1")
                        .description("Registro e consulta de solicitações de ativação de produtos. "
                                + "A ativação no provedor externo é assíncrona: a solicitação é criada como PENDING "
                                + "e o status evolui para COMPLETED ou FAILED."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token OAuth 2.0 (client credentials). Escopos: activation:read e "
                                + "activation:write.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
