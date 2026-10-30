# Serviço de ativação de produtos

API REST que registra solicitações de ativação de produtos de clientes, consulta o status e realiza a
ativação em um provedor externo (`POST /provider/v1/activate`).

**Stack:** Java 21 · Spring Boot 3.5 (Spring MVC, Spring Data JPA/Hibernate, Spring Security Resource Server,
Actuator) · springdoc-openapi (Swagger UI) · Lombok · MapStruct · PostgreSQL · Flyway · Maven · Docker · JUnit 5, Mockito, AssertJ, MockMvc, WireMock,
Testcontainers, Awaitility, ArchUnit.

## Sumário

- [Como executar](#como-executar)
- [API](#api)
- [Decisões técnicas](#decisões-técnicas)
- [Estratégia de testes](#estratégia-de-testes)
- [Cenário de análise](#cenário-de-análise)
- [Produção](#produção)
- [Pontos pendentes](#pontos-pendentes)
- [Melhorias futuras](#melhorias-futuras)
- [Uso de IA](#uso-de-ia)

---

## Como executar

Pré-requisitos: **JDK 21** e **Docker** (Docker é opcional para os testes; sem ele, o teste com
Testcontainers é ignorado automaticamente).

### Build e testes (um comando)

```bash
./mvnw verify          # Linux/macOS
mvnw.cmd verify        # Windows
```

`verify` executa os testes unitários (Surefire, `*Test`) e os de integração (Failsafe, `*IT`).
Apenas os unitários: `./mvnw test`.

### Aplicação + PostgreSQL + mock do provedor (um comando)

```bash
docker compose up --build
```

| Serviço  | Porta  | Descrição |
|----------|--------|-----------|
| app      | `8080` | API |
| app      | `8081` | Actuator (`/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`) |
| wiremock | `8089` | Mock do provedor externo e do JWKS do servidor de autorização |
| postgres | –      | Banco (somente na rede interna do compose) |

### Chamando a API

A API exige um JWT. Para desenvolvimento, `docker/dev-tokens.env` tem tokens assinados por uma chave
descartável cujo JWKS é servido pelo WireMock (válidos até 2100, **apenas** contra o mock):

| Variável            | `sub`      | Escopos |
|---------------------|------------|---------|
| `SYSTEM_A`          | `system-a` | `activation:read activation:write` |
| `SYSTEM_B`          | `system-b` | `activation:read activation:write` |
| `SYSTEM_A_READONLY` | `system-a` | `activation:read` |

```bash
source docker/dev-tokens.env

curl -i -X POST http://localhost:8080/api/v1/requests \
  -H "Authorization: Bearer $SYSTEM_A" -H "Content-Type: application/json" \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'

curl -s http://localhost:8080/api/v1/requests/<id> -H "Authorization: Bearer $SYSTEM_A"
curl -s "http://localhost:8080/api/v1/requests?status=COMPLETED&page=0&size=20" -H "Authorization: Bearer $SYSTEM_A"
```

O mock do provedor responde 200 por padrão. Alguns `productCode` simulam outros cenários:

| `productCode`      | Resposta do mock               | Resultado esperado |
|--------------------|--------------------------------|--------------------|
| `INVALID-PRODUCT`  | 400                            | `FAILED`, sem nova tentativa |
| `UNSTABLE-PRODUCT` | 503 com `Retry-After: 5`       | volta para `PENDING`, nova tentativa a cada 5 s até o limite e então `FAILED` |
| `SLOW-PRODUCT`     | 200 após 8 s (timeout de 5 s)  | timeout → `PENDING`, nova tentativa com a mesma chave de idempotência |

### Swagger (OpenAPI)

Com a aplicação rodando, a documentação interativa fica em:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Especificação OpenAPI (JSON): <http://localhost:8080/v3/api-docs>

Para chamar os endpoints pelo Swagger UI, clique em **Authorize** e cole um dos tokens de
`docker/dev-tokens.env` (apenas o valor, sem `Bearer`). Depois use **Try it out** em qualquer operação.
A documentação é pública; os endpoints continuam exigindo o token e os escopos.

### Rodar a aplicação fora do Docker

```bash
docker compose up -d wiremock                                   # provedor + JWKS
./mvnw spring-boot:run -Dspring-boot.run.profiles=local         # usa H2 em memória
```

Sem o perfil `local`, a aplicação usa PostgreSQL em `localhost:5432` (configurável por `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`).

---

## API

| Método | Caminho                     | Escopo             | Respostas |
|--------|-----------------------------|--------------------|-----------|
| POST   | `/api/v1/requests`          | `activation:write` | `201` criada (header `Location`) · `200` repetição idêntica · `400` · `401` · `403` · `409` |
| GET    | `/api/v1/requests/{id}`     | `activation:read`  | `200` · `400` (id inválido) · `401` · `403` · `404` |
| GET    | `/api/v1/requests?status=&customerId=&page=&size=` | `activation:read` | `200` paginado (`size` 1–100, padrão 20, ordenado por `createdAt` desc) |

**Validação do corpo do POST:** `externalId`, `customerId` e `productCode` são obrigatórios, têm no máximo
64 caracteres e aceitam apenas letras, números, `.`, `_` e `-`.

**Resposta:** `id`, `externalId`, `customerId`, `productCode`, `status`, `attempts`, `lastError` (quando há),
`createdAt`, `updatedAt`. Datas em UTC, ISO-8601. DTOs (`record`) são separados da entidade JPA.

**Erros** seguem o Problem Details (RFC 9457) com `traceId`, o mesmo valor do header `X-Correlation-Id`
(enviado pelo cliente ou gerado) e presente em todos os logs da requisição:

```json
{
  "type": "https://api.exemplo.com/problems/validation-error",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos não passaram na validação.",
  "instance": "/api/v1/requests",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [{ "field": "customerId", "message": "é obrigatório" }]
}
```

| Situação                  | Status | Observação |
|---------------------------|--------|------------|
| Dados inválidos / JSON malformado / parâmetro inválido | 400 | lista `errors` por campo |
| Registro inexistente (ou de outro sistema) | 404 | |
| Duplicada com conteúdo diferente | 409 | inclui `existingRequestId` |
| Erro de integração | – | o POST não chama o provedor (processamento assíncrono); a falha aparece no status (`PENDING` com `lastError` ou `FAILED`) |
| Erro inesperado | 500 | mensagem genérica; stack trace só no log, ligado pelo `traceId` |

---

## Decisões técnicas

### Organização do código

Padrão MVC em camadas:

```
controller/    ActivationRequestController: recebe HTTP e converte DTO <-> modelo
               ActivationRequestApi: contrato OpenAPI (Swagger) e validações de entrada
service/       interfaces: ActivationRequestService, ActivationProcessingService, ProviderService
  impl/        implementações (regras de negócio, processamento assíncrono, cliente HTTP do provedor)
               e RetryPolicy (regra de nova tentativa, detalhe interno do processamento)
repository/    ActivationRequestRepository (Spring Data JPA) e ActivationRequestSpecifications (filtros)
model/         entidade ActivationRequest e enum ActivationStatus (regras de transição)
dto/
  request/     o que entra na API (CreateActivationRequest, com as validações)
  response/    o que sai da API (ActivationRequestResponse, PageResponse, FieldErrorResponse)
  command/     o que trafega entre controller e service (CreateActivationCommand / CreateActivationResult)
  provider/    o que é trocado com o provedor (ProviderActivationRequest / ProviderActivationResult)
mapper/        ActivationRequestMapper (MapStruct): modelo <-> DTOs
exception/     exceções de negócio
handler/       GlobalExceptionHandler (Problem Details)
utils/         CorrelationIdUtils, BackoffUtils (funções estáticas, sem estado)
config/        segurança, OpenAPI, Clock, cliente HTTP do provedor, filtro de correlação e propriedades
scheduler/     ActivationScheduler: dispara o processamento periódico
```

Como o SOLID aparece no código:

- **S (responsabilidade única):** o controller só traduz HTTP; a criação/consulta
  (`ActivationRequestServiceImpl`), o envio ao provedor (`ActivationProcessingServiceImpl`) e o cliente
  HTTP (`ProviderServiceImpl`) são classes separadas; a regra de nova tentativa fica em `RetryPolicy`; a montagem
  das consultas fica no repositório (`ActivationRequestSpecifications`); o agendador só dispara o processamento;
  os erros ficam todos no `GlobalExceptionHandler`.
- **O (aberto/fechado):** o resultado do provedor é um tipo selado (`ProviderActivationResult`), e o `switch` sobre
  ele é verificado pelo compilador: um novo tipo de resultado obriga a tratar o caso, sem `if` espalhado.
- **L (substituição):** qualquer implementação de `ProviderService` (HTTP real, mock, outro provedor) pode
  ser usada pelo processamento sem mudanças.
- **I (segregação de interfaces):** interfaces pequenas por finalidade. Quem só cria/consulta não vê o
  processamento, e vice-versa.
- **D (inversão de dependência):** controller, scheduler e serviços dependem das **interfaces** em
  `service/`; as classes de `service/impl` só são referenciadas pelo Spring. Isso é verificado por
  ArchUnit (`ArchitectureTest`).

**Mapper (MapStruct):** as conversões (entidade → resposta, corpo da requisição + `sub` do token →
comando, entidade → corpo enviado ao provedor) ficam em `ActivationRequestMapper`. O código é gerado em
tempo de compilação (sem reflexão em runtime) e `unmappedTargetPolicy = ERROR` faz o build falhar se um
campo novo do DTO ficar sem mapeamento.

**Lombok:** `@RequiredArgsConstructor` para injeção por construtor, `@Slf4j` para logs, `@Getter` na entidade
e na exceção. A entidade **não** usa `@Data`/`@Setter`: o status só muda pelos métodos de transição, e um
`equals/hashCode` gerado sobre todos os campos é inadequado para entidades JPA. DTOs são `record`, então
não precisam de Lombok.

Sem abstrações extras: não há interface onde só existiria uma implementação sem ganho (repositório já é
interface do Spring Data; utilitários são funções estáticas). A entidade JPA concentra as regras de
transição de status, para que nenhum código altere o status por setter.

### Estados e transições

```
PENDING ──► PROCESSING ──► COMPLETED
   ▲             │
   └─────────────┤ falha temporária (nova tentativa agendada)
                 └──────► FAILED
```

- As transições são métodos da entidade (`startProcessing`, `complete`, `fail`, `scheduleRetry`), sem setters.
  Uma transição inválida (ex.: `COMPLETED → PROCESSING`) lança `InvalidStatusTransitionException`.
- **Falha temporária volta para `PENDING`** com `attempts` incrementado e `nextAttemptAt` agendado.
  `PROCESSING` significa "há uma chamada em andamento agora"; isso permite detectar solicitações presas
  (instância caiu durante a chamada) apenas pelo tempo em `PROCESSING`.
- **Escrita concorrente:** `@Version` (lock otimista). Duas instâncias que tentem reivindicar a mesma
  solicitação não conseguem as duas: a segunda recebe conflito de versão e desiste.

### Duplicidade (item 3)

**Estratégia:** constraint única no banco em `(client_id, external_id)` + tratamento da violação.

1. Busca por `(clientId, externalId)`; se existir, trata como repetição (caminho comum de retry).
2. Se não existir, faz `INSERT` em transação própria (`saveAndFlush`).
3. Se o `INSERT` violar a constraint (outra requisição venceu a corrida), relê o registro vencedor em uma
   nova transação e trata como repetição.

**Por que "verificar e depois inserir" não basta:** entre o `SELECT` e o `INSERT` existe uma janela em que
duas transações concorrentes veem "não existe" e ambas inserem (check-then-act). Só a constraint única é
atômica; o `SELECT` inicial é só uma otimização. O método de criação **não** é `@Transactional` de propósito:
no PostgreSQL uma transação que sofreu erro fica inutilizável, então a releitura precisa de transação nova.

**Por que `(client_id, external_id)` e não só `external_id`:** o `externalId` é gerado pelo sistema de
origem, e dois sistemas diferentes podem gerar o mesmo valor. Com unicidade global, o sistema B, ao reenviar
um `externalId` do sistema A, receberia o registro de A, um vazamento de dados (BOLA).

| Cenário | Resposta | Justificativa |
|---------|----------|---------------|
| Mesmo `externalId`, mesmo conteúdo (retry legítimo) | **200** com o registro existente (e `Location`) | O cliente faz retry porque não sabe se a primeira chamada funcionou; responder sucesso com o mesmo recurso torna o POST idempotente. 409 obrigaria todo cliente a tratar o retry como erro. O 200 (em vez de 201) informa que nada novo foi criado. |
| Mesmo `externalId`, conteúdo diferente | **409 Conflict**, `type .../duplicate-request`, com `existingRequestId` | É um erro do cliente (reuso de identificador); não sobrescrevemos o registro original. |
| Duas requisições idênticas em paralelo | Uma recebe **201**; a outra **200** com o mesmo registro | A constraint decide quem vence; a perdedora cai no caso "retry legítimo". Se o conteúdo divergir, a perdedora recebe 409. |

Testado com 16 threads simultâneas contra H2 **e PostgreSQL real** (Testcontainers): exatamente 1 registro,
1 resposta "criado" e as demais com o mesmo `id`.

### Integração com o provedor (item 5)

**Assíncrono.** O `POST /api/v1/requests` só grava a solicitação (`PENDING`) e retorna `201`
imediatamente. Um processador em background (`ActivationScheduler`, a cada 1 s) envia as pendentes ao
provedor. O tempo de resposta da API não depende do provedor: se ele ficar lento ou fora do ar, a API
continua respondendo rápido, e as threads HTTP e as conexões do banco não ficam presas esperando.

**Transação x chamada remota (outbox).** A própria tabela funciona como outbox: a solicitação gravada
**é** a mensagem a ser enviada, no mesmo commit. A chamada HTTP nunca acontece com transação aberta:

1. transação curta: `PENDING → PROCESSING` (com `@Version`, só uma instância reivindica);
2. chamada ao provedor, **fora de transação**;
3. transação curta: grava o resultado.

Se a aplicação cair depois do commit do POST, a solicitação continua `PENDING` e é enviada quando
o processador rodar de novo (nesta ou em outra instância). Se cair durante a chamada, ela fica em `PROCESSING`;
após `processing-lease` (2 min, maior que o timeout total da chamada) a reconciliação a devolve para
`PENDING` como falha temporária.

**Timeout é ambíguo → chave de idempotência.** Toda chamada envia `Idempotency-Key: <id da solicitação>`,
o mesmo valor em todas as tentativas. Em um timeout, o provedor pode ter ativado o produto; ao repetir
com a mesma chave, ele deve devolver o resultado original em vez de ativar de novo. *Premissa:* o provedor
suporta chave de idempotência; sem isso, o caminho seria consultar o status no provedor antes de repetir.

**Timeouts explícitos e configuráveis:** conexão 2 s e leitura 5 s (`PROVIDER_CONNECT_TIMEOUT`,
`PROVIDER_READ_TIMEOUT`), no `RestClient` com o `HttpClient` do JDK.

| Retorno do provedor | Novo status | Nova tentativa? | Justificativa |
|---------------------|-------------|-----------------|---------------|
| Sucesso (2xx) | `COMPLETED` | Não | Ativado. |
| HTTP 400 (e demais 4xx, exceto 408/429) | `FAILED` | Não | Erro nos dados/regra de negócio; repetir dá o mesmo resultado. O motivo fica em `lastError`. |
| HTTP 500 (5xx) | `PENDING` (→ `FAILED` no limite) | Sim, com backoff | Falha transitória do provedor. |
| Timeout | `PENDING` (→ `FAILED` no limite) | Sim, com a mesma `Idempotency-Key` | Resultado desconhecido; a chave evita ativação duplicada. |
| Falha de conexão | `PENDING` (→ `FAILED` no limite) | Sim | Transitória; a requisição nem chegou ao provedor. |
| HTTP 429 / 503 com `Retry-After` | `PENDING` com `nextAttemptAt = Retry-After` | Sim, no tempo indicado (limitado a `max-backoff`) | O provedor informou quando voltar; respeitamos em vez de usar o backoff. |

**Limite de tentativas e backoff:** até 5 tentativas; backoff exponencial a partir de 2 s, limitado a 5 min,
com *jitter* (metade fixa + metade aleatória) para que solicitações que falharam juntas não voltem juntas.
Ao atingir o limite: `FAILED` com `lastError = "Limite de 5 tentativas atingido. Último erro: ..."`.
Uma solicitação `FAILED` pode ser reprocessada manualmente ou por uma ferramenta operacional (ver pendentes).

**Circuit breaker:** não implementado. Com o processamento assíncrono e o backoff, o provedor fora do ar
não derruba a API nem gera tempestade de chamadas. Com alto volume, eu colocaria um circuit breaker
(Resilience4j) no `ProviderServiceImpl`: com o circuito aberto, o resultado vira `TemporaryFailure` sem
chamar o provedor, e as solicitações ficam `PENDING`.

### Segurança (item 7, implementado)

- **Fluxo OAuth 2.0:** *Client Credentials*. A comunicação é entre sistemas, sem usuário final; cada sistema
  consumidor é um client no servidor de autorização (Keycloak, Entra ID, Cognito, Auth0...) e obtém um
  access token JWT de curta duração.
- **Spring Security como Resource Server.** O serviço valida no JWT:
  - **assinatura**, com as chaves públicas do **JWKS** do emissor (`JWT_JWK_SET_URI`), selecionadas pelo
    `kid`; a rotação de chaves no servidor de autorização é transparente;
  - **emissor** (`iss` = `JWT_ISSUER`), **audiência** (`aud` contém `activation-service`) e
    **expiração/nbf** (com tolerância de relógio padrão de 60 s).
- **Autorização por escopo:** `activation:write` para criar; `activation:read` para consultar/listar.
  Um sistema pode ter só leitura.
- **Isolamento entre consumidores (OWASP API1 – BOLA):** o `sub` do token (o client) é gravado em
  `client_id` e toda consulta filtra por ele. O controller recebe esse valor pronto com `@CurrentClientId`
  (anotação sobre o `@AuthenticationPrincipal` do Spring Security), sem conhecer JWT. A obrigatoriedade do
  `sub` é uma regra de validação do token: um validador extra no `JwtDecoder` rejeita com 401 tokens sem ele. Pedir o `id` de outro sistema retorna **404** (não 403, para
  não revelar que o recurso existe).
- **Credencial do provedor externo:** lida de `PROVIDER_API_KEY` (enviada em `X-Api-Key`), nunca do
  repositório. Em produção, ficaria no gerenciador de segredos da cloud (AWS Secrets Manager, Azure Key
  Vault, Vault), injetada como variável de ambiente ou montada como arquivo. Para rotacionar sem downtime,
  o provedor aceita duas chaves válidas durante a troca: cadastra a nova, faz o deploy (ou recarrega o
  segredo) e revoga a antiga. Se o provedor suportar OAuth, melhor ainda: client credentials com token de
  curta duração, sem segredo estático de longa vida.
- Testes de 401 (sem token, token adulterado) e 403 (escopo insuficiente) rodam sem servidor de
  autenticação real (`spring-security-test`).
- Actuator na porta de gerenciamento `8081` (separada da API), que em produção não é exposta publicamente.

### Persistência e índices

Migrations versionadas com **Flyway** (`src/main/resources/db/migration`); o Hibernate só **valida** o
schema (`ddl-auto=validate`). O teste com PostgreSQL real também valida migrations e mapeamento.

| Índice | Uso |
|--------|-----|
| `UNIQUE (client_id, external_id)` | Regra de duplicidade e busca do retry. |
| `(status, next_attempt_at)` | Processador: buscar `PENDING` já vencidas, em ordem. |
| `(status, updated_at)` | Reconciliação: buscar `PROCESSING` antigas. |
| `(client_id, status, created_at)` | Listagem filtrada por status, ordenada por data. |
| `(client_id, customer_id, created_at)` | Listagem filtrada por cliente, ordenada por data. |

Toda listagem começa por `client_id` (isolamento), por isso ele é a primeira coluna dos índices de listagem.
`created_at` no fim permite à paginação ordenada usar o índice sem ordenar em memória. No PostgreSQL, os
índices do processador poderiam ser parciais (`WHERE status = 'PENDING'`), bem menores, já que a maioria
das linhas fica em estado final; mantive índices comuns para a mesma migration rodar no H2 local.

### Observabilidade

- **Correlação:** `X-Correlation-Id` é aceito (validado contra injeção em log) ou gerado, devolvido na
  resposta, colocado no MDC, gravado na solicitação e **propagado ao provedor**, inclusive no processamento
  assíncrono. O mesmo valor é o `traceId` dos erros.
- **Actuator:** `liveness` e `readiness` (readiness inclui o banco), `prometheus`.
- **Métricas:** `http.server.requests` (latência da API, com histograma), `provider.activation`
  (latência do provedor por `outcome`: activated / rejected / temporary_failure, de onde sai a taxa de
  erro) e `activation.requests.attempts` (resultado de cada tentativa por status). Métricas do Hikari
  (pool de conexões) e da JVM vêm automaticamente.
- **Logs padronizados com `LogBuilder`** (`utils/`): todo log é uma mensagem curta seguida de campos
  `chave=valor`, por exemplo
  `Provider result applied requestId=0b7f... status=PENDING attempts=1 lastError=Provedor indisponível (HTTP 503)`.
  Os mesmos campos vão como key-value pairs do SLF4J, então no JSON viram atributos pesquisáveis.
  Uma regra do ArchUnit impede chamar `log.info/warn/...` diretamente fora do `LogBuilder`.
- **Logs estruturados em JSON (ECS)** no container (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), com o
  `correlationId`. Tokens e credenciais não são logados.

---

## Estratégia de testes

São 123 testes. Os unitários rodam em segundos e não dependem de Spring nem de banco; os de integração
cobrem o que só existe com as peças reais: banco, HTTP, segurança, concorrência.

| Teste | Tipo | O que garante |
|-------|------|---------------|
| `ActivationRequestTest` | Unitário (sem Spring) | Transições válidas e inválidas, estados finais, contador de tentativas. |
| `BackoffUtilsTest` | Unitário | Backoff exponencial dentro dos limites do jitter, teto, `Retry-After`. |
| `RetryPolicyTest` | Unitário | Nova tentativa com backoff, uso do `Retry-After` e `FAILED` ao atingir o limite. |
| `RetryAfterUtilsTest` | Unitário | Header `Retry-After` em segundos, como data HTTP, data passada e valor inválido. |
| `ActivationRequestServiceImplTest` | Unitário (Mockito) | Regras de duplicidade, inclusive a corrida (violação da constraint → relê o vencedor). |
| `ProviderServiceImplTest` | Integração do cliente HTTP (WireMock) | Classificação de cada retorno (2xx, 4xx, 5xx, 408, 429, `Retry-After`, timeout, conexão recusada) e headers enviados (idempotência, correlação, credencial). |
| `ActivationRequestControllerTest` | Fatia web (`@WebMvcTest`) | Contrato HTTP: 201 + `Location`, 200 no retry, 400 com Problem Details, 404, 409, 500 sem detalhes internos, **401 e 403**. |
| `LogBuilderTest` | Unitário | Formato padrão da mensagem, key-value pairs, exceção e nível desabilitado. |
| `ArchitectureTest` | ArchUnit | Regras de dependência entre camadas (controller só usa interfaces de serviço, serviços não conhecem a camada web, model independente) e logs sempre via `LogBuilder`. |
| `ActivationFlowIT` | Integração (`@SpringBootTest` + H2 + WireMock) | Fluxo completo: criação → processamento → status final para sucesso, 400, 500 até o limite, 503 seguido de sucesso, `Retry-After`, timeout, reconciliação de `PROCESSING` presa, retry idempotente, 409, isolamento entre clientes, listagem paginada. |
| `OpenApiDocsIT` | Integração (`@SpringBootTest`) | Swagger UI e `/v3/api-docs` acessíveis sem token, com os endpoints, os códigos de resposta e o esquema Bearer JWT; o `clientId` do token não aparece como parâmetro. |
| `JwtValidationIT` | Integração (`@SpringBootTest`) | O `JwtDecoder` da aplicação aceita token com `sub` e rejeita token sem `sub`. |
| `ActivationSchedulerIT` | Integração (Awaitility) | O agendador processa sozinho em background. |
| `H2ConcurrentCreationIT` / `PostgresConcurrentCreationIT` | Integração (Testcontainers) | 16 requisições simultâneas com o mesmo `externalId` → exatamente 1 registro. |

Nos testes de integração o agendador fica desligado e o teste chama o processador diretamente, o que
deixa o resultado determinístico; o agendamento em si é coberto por um teste separado.

---

## Cenário de análise

> Tempo médio da API 300 ms → 2,5 s · CPU 45% · Memória 60% · algumas consultas 50 ms → 1,2 s ·
> serviço externo 400 ms → 3 s.

**O que eu investigaria primeiro.** CPU e memória normais indicam que a aplicação não está calculando
mais: está **esperando** (I/O, pool de conexões, locks). Primeiro eu veria **o que mudou e quando**:
deploy, aumento de tráfego, crescimento de tabelas, mudança no provedor. Depois, a latência por endpoint
em **p95/p99**, não só a média, para saber se é tudo lento ou uma rota específica.

**Como separar aplicação, banco e integração.** Decompondo o tempo de uma requisição lenta:
- **Tracing distribuído** (ou os logs com `correlationId`): quanto do tempo total é span de banco, de chamada
  HTTP ou "buraco" (espera por conexão/thread)?
- **Pool de conexões (Hikari):** `hikaricp_connections_pending` > 0 e `connections_acquire` alto mostram
  requisições esperando *conexão*, e não a query em si.
- **Banco:** `pg_stat_statements` (queries que pioraram), `EXPLAIN (ANALYZE, BUFFERS)` nelas,
  `pg_stat_activity` (sessões *idle in transaction*, locks), CPU/IO do banco.
- **Provedor:** `provider_activation_seconds` por outcome, taxa de timeouts; comparar com a métrica do
  lado do provedor (se a latência dele é a mesma que medimos, o problema é dele; se não, é rede/DNS/nosso pool).
- **Aplicação:** threads do Tomcat ocupadas, pausas de GC, thread dump (onde as threads estão paradas).

**Logs e métricas úteis.** Latência por endpoint (histograma), métricas do Hikari, latência e erros do
provedor, slow query log / `pg_stat_statements`, threads ocupadas, GC, e logs estruturados com
`correlationId` e duração de cada etapa.

**Melhorias possíveis.** Índice para a query que piorou (provável crescimento de tabela com *seq scan*);
timeouts e circuit breaker na integração; tirar a chamada externa do caminho síncrono (como feito neste
serviço); revisar o tamanho do pool de conexões; paginação/limites; cache para dados pouco voláteis.

**Uma causa comum (hipótese).** Se a chamada ao provedor acontecesse **dentro de uma transação** (ou
com a conexão do banco reservada, como no padrão *open-session-in-view*), a lentidão do provedor
(400 ms → 3 s) manteria cada conexão presa ~7× mais tempo. O pool esgota, e as outras requisições passam
a **esperar conexão**, o que aparece como "consultas lentas" quando a medição inclui a aquisição da
conexão. A API fica lenta como um todo, e a CPU continua baixa porque as threads só esperam.
**Como confirmar:** `hikaricp_connections_active` no máximo e `pending` > 0 no mesmo intervalo em que o
provedor degradou; `pg_stat_activity` com sessões *idle in transaction*; no trace, o tempo "de query"
está na aquisição da conexão. Thread dumps mostram threads paradas em `HikariPool.getConnection` e em
leitura de socket do cliente HTTP. *Prevenção neste serviço:* chamada ao provedor fora de transação e
em background, `open-in-view=false`, timeouts explícitos.

**Como validar a correção depois de publicada.** Comparar p95/p99 da API, latência das queries e uso do
pool antes e depois, com tráfego comparável. Publicar como *canary* (uma parte do tráfego) e comparar com a
versão anterior. Ter alertas por SLO (ex.: p99 < 500 ms) e, antes do deploy, reproduzir o cenário em
homologação com teste de carga e o provedor lento simulado (WireMock com delay).

---

## Produção

**Imagem:** `Dockerfile` multi-stage (build com JDK, runtime só com JRE Alpine), camadas do Spring Boot
extraídas (deploys menores), usuário não-root, `MaxRAMPercentage=75` (heap proporcional ao limite de memória
do container), `ExitOnOutOfMemoryError`, `HEALTHCHECK` na readiness, configuração 100% por variáveis de
ambiente, *graceful shutdown* (requisições em andamento e o ciclo do processador terminam antes de parar).

**Como eu executaria:**
- Orquestrador de containers (Kubernetes, ECS ou similar) com **2+ réplicas** em zonas diferentes,
  *rolling update*, *liveness/readiness probes* nos endpoints do Actuator (porta 8081, interna),
  *requests/limits* de CPU e memória, autoscaling por CPU/latência.
- **PostgreSQL gerenciado** (RDS/Cloud SQL/Azure Database) com backup, réplica e alta disponibilidade.
  Migrations Flyway executadas na subida da aplicação (ou como job separado antes do deploy, em equipes
  maiores).
- Exposição via **API Gateway / Ingress** com TLS, limite de tamanho de corpo e *rate limiting* por client.
- Segredos (`DB_PASSWORD`, `PROVIDER_API_KEY`) do gerenciador de segredos; demais configurações por
  variáveis de ambiente/ConfigMap.
- Logs JSON para a plataforma central (ELK, Datadog, CloudWatch); Prometheus coletando `/actuator/prometheus`;
  alertas de latência, taxa de erro do provedor, solicitações em `FAILED` e idade da `PENDING` mais antiga.

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | localhost / `activation` / 10 | Banco |
| `JWT_JWK_SET_URI`, `JWT_ISSUER`, `JWT_AUDIENCE` | mock / `https://auth.dev.local` / `activation-service` | Validação do JWT |
| `PROVIDER_BASE_URL`, `PROVIDER_API_KEY` | `http://localhost:8089` / vazio | Provedor externo |
| `PROVIDER_CONNECT_TIMEOUT`, `PROVIDER_READ_TIMEOUT` | `2s` / `5s` | Timeouts do cliente HTTP |
| `PROCESSING_SCHEDULER_ENABLED` | `true` | Liga/desliga o processador nesta instância |
| `SWAGGER_ENABLED` | `true` | Expõe o Swagger UI e o `/v3/api-docs` (pode ser desligado em produção) |
| `MANAGEMENT_SERVER_PORT` | `8081` (na imagem) | Porta do Actuator |

### Microsserviços e cloud

- **Várias instâncias:** a duplicidade é garantida pela constraint do banco, não pela memória da instância.
  Todas as instâncias rodam o processador; o `@Version` garante que só uma reivindica cada solicitação.
  Com muito volume, eu trocaria a reivindicação otimista por
  `SELECT ... FOR UPDATE SKIP LOCKED`, em que cada instância pega um lote diferente sem disputa.
- **Configurações e segredos:** variáveis de ambiente para configuração e o serviço de segredos da cloud
  para credenciais, sem nada sensível na imagem ou no repositório.
- **Avisar outros serviços sobre a ativação concluída:** publicar `ActivationCompleted` em um tópico
  (Kafka, SNS/SQS, Service Bus) usando **outbox transacional**: o evento é gravado em uma tabela de outbox
  na mesma transação que muda o status para `COMPLETED`, e um relay (ou Debezium/CDC) publica. Assim não
  existe "status mudou mas o evento se perdeu". A entrega é *at-least-once*, então os consumidores devem
  ser idempotentes (pelo `id` da solicitação).
- **CI/CD:** o pipeline (`.github/workflows/ci.yml`) já faz build, testes (inclusive Testcontainers) e build
  da imagem. Continuação: scan de vulnerabilidades da imagem (Trivy), push para o registry com a tag do
  commit, deploy automático em homologação com smoke test, e promoção da **mesma imagem** para produção
  com aprovação, via canary/blue-green e rollback automático por métricas.

---

## Pontos pendentes

| Item | Situação | Como faria |
|------|----------|------------|
| Rate limiting e limite de tamanho do corpo | Não implementado | No API Gateway/Ingress (limite por client do token e `max body size`). Na aplicação: Bucket4j por `sub` e um filtro rejeitando `Content-Length` acima de alguns KB com 413. A validação já limita cada campo a 64 caracteres. |
| Circuit breaker | Não implementado (ver justificativa na integração) | Resilience4j em volta do `ProviderServiceImpl`. |
| Tracing distribuído (OpenTelemetry) | Correlação feita com `X-Correlation-Id` próprio | `micrometer-tracing-bridge-otel` + exportador OTLP, com propagação W3C `traceparent`; o `traceId` do OTel substituiria o identificador próprio. |
| Corpo das respostas 401/403 | Comportamento padrão do Spring Security (sem corpo, com header `WWW-Authenticate`) | `AuthenticationEntryPoint` e `AccessDeniedHandler` próprios escrevendo Problem Details. |
| Reprocessar uma solicitação `FAILED` | Não implementado (estado final) | Endpoint administrativo com escopo próprio criando uma nova tentativa, com auditoria. |
| Métrica de solicitações por status (gauge) | Há contadores de tentativas por resultado | Gauge com contagem por status via query periódica, e "idade da `PENDING` mais antiga" para alertar backlog. |
| Paralelismo do processador | Processa um lote em sequência por instância | Executor com virtual threads (ou pool limitado, como *bulkhead*) para processar o lote em paralelo; a reivindicação com `@Version` já é segura para isso. |

## Melhorias futuras

- `SELECT ... FOR UPDATE SKIP LOCKED` e índices parciais no PostgreSQL para o processador em alto volume.
- Processar logo após o commit (evento `AFTER_COMMIT`), mantendo o polling apenas como rede de segurança,
  para reduzir a latência até a ativação.
- Publicação do evento de ativação concluída via outbox transacional.
- Política de retenção/arquivamento das solicitações finalizadas.
- Testes de contrato com o provedor (Pact ou Spring Cloud Contract) e teste de carga (Gatling/k6) no pipeline.
- Página de runbook: o que fazer com solicitações em `FAILED`, alertas e dashboards.

## Uso de IA

Utilizei IA (assistente do Cursor) como apoio na escrita dos testes unitários, na documentação
(README, ADRs, Javadoc e descrições do Swagger) e em alguns pontos da configuração do WireMock.
Todo o conteúdo foi validado executando a suíte completa (`./mvnw verify`, inclusive o teste com PostgreSQL
via Testcontainers) e testes manuais de ponta a ponta no `docker compose` e no Swagger UI.
