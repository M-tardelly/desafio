package com.example.activation.controller;

import com.example.activation.dto.ActivationRequestResponse;
import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.dto.CreateActivationRequest;
import com.example.activation.dto.CreationResult;
import com.example.activation.dto.PageResponse;
import com.example.activation.mapper.ActivationRequestMapper;
import com.example.activation.model.ActivationStatus;
import com.example.activation.service.ActivationRequestService;
import com.example.activation.utils.CorrelationIdUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class ActivationRequestController {

    private final ActivationRequestService service;
    private final ActivationRequestMapper mapper;

    /**
     * 201 quando a solicitação é criada; 200 com o registro existente quando é uma repetição
     * com o mesmo conteúdo (retry do sistema de origem).
     */
    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequest body,
                                                            @AuthenticationPrincipal Jwt jwt) {
        CreateActivationCommand command = mapper.toCommand(body, clientId(jwt), CorrelationIdUtils.current());
        CreationResult result = service.create(command);
        ActivationRequestResponse response = mapper.toResponse(result.request());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return result.created()
                ? ResponseEntity.created(location).body(response)
                : ResponseEntity.ok().location(location).body(response);
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(service.findForClient(id, clientId(jwt)));
    }

    @GetMapping
    public PageResponse<ActivationRequestResponse> list(
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return mapper.toPageResponse(service.list(clientId(jwt), status, customerId, page, size));
    }

    /**
     * O sistema consumidor é identificado pelo "sub" do token (no client credentials, o client_id).
     * Toda leitura é filtrada por ele para que um sistema não acesse solicitações de outro.
     */
    private static String clientId(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new AccessDeniedException("Token sem identificação do sistema consumidor");
        }
        return jwt.getSubject();
    }
}
