# ADR 0001 – Duplicidade garantida por constraint única no banco

**Status:** aceito

## Contexto

O `externalId` identifica uma solicitação do sistema de origem, que faz retry. Duas requisições iguais
podem chegar quase ao mesmo tempo, inclusive em instâncias diferentes da aplicação.

## Decisão

- Constraint `UNIQUE (client_id, external_id)` no banco; ela é a fonte da verdade.
- A criação tenta o `INSERT` em transação própria; se a constraint for violada, relê o registro vencedor em
  outra transação.
- Repetição com o mesmo conteúdo → `200` com o registro existente. Conteúdo diferente → `409`.
- O escopo da unicidade inclui o `client_id` (o `sub` do token).

## Alternativas consideradas

- **Verificar e depois inserir:** tem condição de corrida (check-then-act). Rejeitada como mecanismo único;
  mantida apenas como otimização do caminho comum.
- **Lock pessimista / lock distribuído (Redis):** mais infraestrutura e mais pontos de falha para algo que
  o banco já resolve de forma atômica.
- **`INSERT ... ON CONFLICT DO NOTHING`:** atômico e sem exceção, mas específico do PostgreSQL; a solução
  escolhida funciona igual no H2 usado localmente. Seria uma troca simples se o banco for fixo.
- **Unicidade global de `external_id`:** dois sistemas podem gerar o mesmo valor, e o retry de um sistema
  devolveria dados do outro (BOLA).

## Consequências

- Correto com qualquer número de instâncias.
- O método de criação não pode ser `@Transactional` (no PostgreSQL a transação que sofreu erro fica
  inutilizável), o que está documentado no código.
