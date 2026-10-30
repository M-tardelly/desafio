package com.example.activation.controller;

import com.example.activation.config.CurrentClientId;
import com.example.activation.dto.command.ActivationRequestQuery;
import com.example.activation.dto.command.CreateActivationCommand;
import com.example.activation.dto.command.CreateActivationResult;
import com.example.activation.dto.request.CreateActivationRequest;
import com.example.activation.dto.response.ActivationRequestResponse;
import com.example.activation.dto.response.PageResponse;
import com.example.activation.mapper.ActivationRequestMapper;
import com.example.activation.model.ActivationStatus;
import com.example.activation.service.ActivationRequestService;
import com.example.activation.utils.CorrelationIdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

/**
 * Endpoints REST de solicitações de ativação ({@code /api/v1/requests}).
 * <p>
 * Todas as operações recebem o sistema consumidor autenticado ({@link CurrentClientId}) e só enxergam
 * as solicitações dele, para que um sistema não acesse solicitações de outro. A documentação OpenAPI e as
 * validações de entrada ficam em {@link ActivationRequestApi}.
 *
 * @since 2026-10-29
 */
@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class ActivationRequestController implements ActivationRequestApi {

    private final ActivationRequestService service;
    private final ActivationRequestMapper mapper;

    @Override
    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@RequestBody CreateActivationRequest body,
                                                            @CurrentClientId String clientId) {
        CreateActivationCommand command = mapper.toCommand(body, clientId, CorrelationIdUtils.current());
        CreateActivationResult result = service.create(command);
        ActivationRequestResponse response = mapper.toResponse(result.request());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return result.created()
                ? ResponseEntity.created(location).body(response)
                : ResponseEntity.ok().location(location).body(response);
    }

    @Override
    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id, @CurrentClientId String clientId) {
        return mapper.toResponse(service.findForClient(id, clientId));
    }

    @Override
    @GetMapping
    public PageResponse<ActivationRequestResponse> list(
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentClientId String clientId) {
        ActivationRequestQuery query = new ActivationRequestQuery(clientId, status, customerId, page, size);
        return mapper.toPageResponse(service.list(query));
    }
}
