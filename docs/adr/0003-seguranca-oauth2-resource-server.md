# ADR 0003 – OAuth 2.0 Client Credentials com Spring Security Resource Server

**Status:** aceito

## Contexto

A API é consumida por outros sistemas (sem usuário final). É preciso autenticar o sistema chamador,
separar quem cria de quem só consulta e impedir que um sistema acesse solicitações de outro.

## Decisão

- Fluxo **Client Credentials**; o serviço é um **Resource Server** que valida JWTs.
- Validação: assinatura (JWKS do emissor, por `kid`), `iss`, `aud` = `activation-service`, `exp`/`nbf`.
- Escopos: `activation:write` (POST) e `activation:read` (GET).
- `sub` do token = identificador do sistema consumidor, gravado em `client_id`; todas as leituras filtram
  por ele (proteção contra BOLA). Recurso de outro sistema → 404.
- Ambiente de desenvolvimento: o WireMock serve um JWKS de uma chave descartável, e
  `docker/dev-tokens.env` traz tokens assinados por ela. Em produção basta apontar `JWT_JWK_SET_URI` e
  `JWT_ISSUER` para o servidor de autorização real.

## Alternativas consideradas

- **API key por sistema:** simples, mas sem expiração, escopos ou rotação padronizada.
- **Token opaco com introspecção:** revogação imediata, mas uma chamada ao servidor de autorização a cada
  requisição; o JWT é validado localmente.
- **mTLS:** forte para autenticação entre serviços, complementar ao JWT (normalmente na malha de serviço).

## Consequências

- Nenhum segredo de autenticação dos consumidores fica no serviço; a rotação de chaves é feita pelo emissor.
- Revogação depende da expiração curta do token.
