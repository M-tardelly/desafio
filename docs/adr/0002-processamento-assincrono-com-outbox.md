# ADR 0002 – Ativação assíncrona usando a tabela de solicitações como outbox

**Status:** aceito

## Contexto

Após criar a solicitação é preciso chamar o provedor externo, que pode ficar lento, fora do ar ou
responder com timeout (resultado desconhecido). Não queremos chamada HTTP com transação de banco aberta,
e a solicitação gravada precisa chegar ao provedor mesmo se a aplicação cair logo após o commit.

## Decisão

- O `POST` grava a solicitação como `PENDING` e responde `201` sem chamar o provedor.
- Um processador agendado (polling a cada 1 s) busca as `PENDING` vencidas e, para cada uma:
  1. transação curta `PENDING → PROCESSING`, com `@Version` (só uma instância vence);
  2. chamada HTTP fora de transação, com `Idempotency-Key` = id da solicitação;
  3. transação curta gravando o resultado (`COMPLETED`, `FAILED` ou nova tentativa em `PENDING`).
- Solicitações em `PROCESSING` há mais tempo que `processing-lease` são reagendadas (reconciliação).
- Falhas temporárias: backoff exponencial com jitter, `Retry-After` respeitado, limite de tentativas.

## Alternativas consideradas

- **Síncrono no POST:** latência da API passa a depender do provedor; threads e conexões presas quando ele
  degrada; o cliente recebe erro por algo que poderia ser tentado de novo depois.
- **`@Async` / evento após commit, sem persistência do "a enviar":** perde a mensagem se a instância cair
  entre o commit e a chamada.
- **Fila (Kafka/SQS) já nesta versão:** resolveria a distribuição, mas exige outbox do mesmo jeito para não
  perder mensagens e adiciona infraestrutura desnecessária para o escopo.

## Consequências

- Latência de até ~1 s (intervalo de polling) até a ativação começar; aceitável aqui e reduzível com
  processamento imediato após o commit, mantendo o polling como rede de segurança.
- Polling gera carga no banco proporcional ao número de instâncias; mitigada por índice em
  `(status, next_attempt_at)` e, em alto volume, `FOR UPDATE SKIP LOCKED`.
- Entrega *at-least-once* ao provedor; a duplicidade é evitada pela chave de idempotência.
