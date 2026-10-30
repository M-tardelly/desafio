package com.example.activation.mapper;

import com.example.activation.dto.ActivationRequestResponse;
import com.example.activation.dto.CreateActivationCommand;
import com.example.activation.dto.CreateActivationRequest;
import com.example.activation.dto.PageResponse;
import com.example.activation.dto.ProviderActivationRequest;
import com.example.activation.model.ActivationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivationRequestMapper {

    ActivationRequestResponse toResponse(ActivationRequest request);

    CreateActivationCommand toCommand(CreateActivationRequest body, String clientId, String correlationId);

    @Mapping(target = "requestId", source = "id")
    ProviderActivationRequest toProviderRequest(ActivationRequest request);

    default PageResponse<ActivationRequestResponse> toPageResponse(Page<ActivationRequest> page) {
        return new PageResponse<>(page.getContent().stream().map(this::toResponse).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
