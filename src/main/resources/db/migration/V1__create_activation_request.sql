CREATE TABLE activation_request
(
    id              UUID                     NOT NULL PRIMARY KEY,
    client_id       VARCHAR(100)             NOT NULL,
    external_id     VARCHAR(64)              NOT NULL,
    customer_id     VARCHAR(64)              NOT NULL,
    product_code    VARCHAR(64)              NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    attempts        INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(500),
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT                   NOT NULL,
    CONSTRAINT uk_activation_request_client_external_id UNIQUE (client_id, external_id),
    CONSTRAINT ck_activation_request_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Busca de trabalho pelo processador: status = PENDING/PROCESSING ordenado por next_attempt_at/updated_at.
CREATE INDEX ix_activation_request_status_next_attempt ON activation_request (status, next_attempt_at);
CREATE INDEX ix_activation_request_status_updated ON activation_request (status, updated_at);

-- Listagem paginada: sempre filtrada por client_id, com filtros opcionais e ordenação por created_at.
CREATE INDEX ix_activation_request_client_status_created ON activation_request (client_id, status, created_at);
CREATE INDEX ix_activation_request_client_customer_created ON activation_request (client_id, customer_id, created_at);
