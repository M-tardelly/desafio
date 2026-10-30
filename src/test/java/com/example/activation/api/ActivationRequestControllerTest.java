package com.example.activation.api;

import com.example.activation.application.ActivationRequestService;
import com.example.activation.application.CreateActivationCommand;
import com.example.activation.application.CreationResult;
import com.example.activation.application.DuplicateRequestConflictException;
import com.example.activation.application.RequestNotFoundException;
import com.example.activation.config.SecurityConfig;
import com.example.activation.domain.ActivationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationRequestController.class)
@Import(SecurityConfig.class)
class ActivationRequestControllerTest {

    private static final String VALID_BODY = """
            {"externalId": "EXT-12345", "customerId": "CUST-10001", "productCode": "INTERNET-500"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivationRequestService service;

    private static JwtRequestPostProcessor client(String... scopes) {
        var authorities = Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(token -> token.subject("system-a")).authorities(authorities);
    }

    private static ActivationRequest sampleRequest() {
        return ActivationRequest.create("system-a", "EXT-12345", "CUST-10001", "INTERNET-500", "corr",
                Instant.parse("2026-10-26T14:03:11Z"));
    }

    @Test
    void createsRequestAndReturnsLocation() throws Exception {
        var request = sampleRequest();
        when(service.create(any())).thenReturn(new CreationResult(request, true));

        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/requests/" + request.getId())))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id").value(request.getId().toString()))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-26T14:03:11Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-26T14:03:11Z"));
    }

    @Test
    void usesTokenSubjectAsClientAndPropagatesCorrelationId() throws Exception {
        var expected = new CreateActivationCommand("system-a", "EXT-12345", "CUST-10001", "INTERNET-500", "abc-123");
        when(service.create(eq(expected))).thenReturn(new CreationResult(sampleRequest(), true));

        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .header("X-Correlation-Id", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void returns200WithExistingRequestOnRetry() throws Exception {
        when(service.create(any())).thenReturn(new CreationResult(sampleRequest(), false));

        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-12345"));
    }

    @Test
    void rejectsInvalidPayloadWithProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .header("X-Correlation-Id", "trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId": "EXT 1", "productCode": "%s"}
                                """.formatted("P".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.type").value("https://api.exemplo.com/problems/validation-error"))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests"))
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[?(@.field == 'customerId')].message").value("é obrigatório"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void returns409WhenSameExternalIdHasDifferentContent() throws Exception {
        var existingId = UUID.randomUUID();
        when(service.create(any())).thenThrow(new DuplicateRequestConflictException("EXT-12345", existingId));

        mockMvc.perform(post("/api/v1/requests").with(client("activation:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"))
                .andExpect(jsonPath("$.existingRequestId").value(existingId.toString()));
    }

    @Test
    void returnsRequestById() throws Exception {
        var request = sampleRequest();
        when(service.findForClient(request.getId(), "system-a")).thenReturn(request);

        mockMvc.perform(get("/api/v1/requests/{id}", request.getId()).with(client("activation:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returns404WhenRequestDoesNotExist() throws Exception {
        var id = UUID.randomUUID();
        when(service.findForClient(id, "system-a")).thenThrow(new RequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(client("activation:read")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitação não encontrada"));
    }

    @Test
    void returns400ForInvalidId() throws Exception {
        mockMvc.perform(get("/api/v1/requests/not-a-uuid").with(client("activation:read")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesPaginationParameters() throws Exception {
        mockMvc.perform(get("/api/v1/requests").param("size", "500").with(client("activation:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void hidesInternalDetailsOnUnexpectedErrors() throws Exception {
        var id = UUID.randomUUID();
        when(service.findForClient(id, "system-a"))
                .thenThrow(new IllegalStateException("connection pool exhausted at db-host:5432"));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(client("activation:read")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Erro inesperado"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(content().string(not(containsString("db-host"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returns403WhenReadOnlyClientTriesToCreate() throws Exception {
        mockMvc.perform(post("/api/v1/requests").with(client("activation:read"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void returns403WhenWriteOnlyClientTriesToRead() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()).with(client("activation:write")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
