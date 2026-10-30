package com.example.activation.repository;

import com.example.activation.model.ActivationRequest;
import com.example.activation.model.ActivationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filtros combináveis da listagem. Filtros opcionais sem valor não restringem a consulta.
 *
 * @since 2026-10-29
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActivationRequestSpecifications {

    /**
     * Restringe às solicitações do sistema consumidor.
     *
     * @param clientId sistema consumidor
     * @return filtro obrigatório pelo dono da solicitação
     */
    public static Specification<ActivationRequest> belongsTo(String clientId) {
        return (root, query, cb) -> cb.equal(root.get("clientId"), clientId);
    }

    /**
     * Filtra por status, quando informado.
     *
     * @param status status desejado, ou {@code null} para não filtrar
     * @return filtro por status
     */
    public static Specification<ActivationRequest> hasStatus(ActivationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * Filtra por cliente, quando informado.
     *
     * @param customerId cliente desejado, ou vazio para não filtrar
     * @return filtro por cliente
     */
    public static Specification<ActivationRequest> hasCustomer(String customerId) {
        return (root, query, cb) ->
                StringUtils.hasText(customerId) ? cb.equal(root.get("customerId"), customerId) : null;
    }
}
