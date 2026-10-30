package com.example.activation.repository;

import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filtros combináveis da listagem. Filtros opcionais sem valor não restringem a consulta.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActivationRequestSpecifications {

    public static Specification<ActivationRequest> belongsTo(String clientId) {
        return (root, query, cb) -> cb.equal(root.get("clientId"), clientId);
    }

    public static Specification<ActivationRequest> hasStatus(ActivationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<ActivationRequest> hasCustomer(String customerId) {
        return (root, query, cb) ->
                StringUtils.hasText(customerId) ? cb.equal(root.get("customerId"), customerId) : null;
    }
}
