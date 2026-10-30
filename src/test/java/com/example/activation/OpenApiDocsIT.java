package com.example.activation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que a documentação OpenAPI e o Swagger UI estão acessíveis sem token e descrevem a API corretamente.
 *
 * @since 2026-10-29
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesApiDocumentationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Activation Service API"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void doesNotExposeAuthenticatedClientAsParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].get.parameters[*].name",
                        not(hasItem("clientId"))))
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get.parameters[*].name",
                        not(hasItem("clientId"))))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].get.parameters[*].name", hasItem("status")));
    }

    @Test
    void documentsResponseCodes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get.responses['404']").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
