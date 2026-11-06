# Estados da operação, falhas de integração e trilha de eventos

## Estados

| Estado | Significado |
|---|---|
| `PENDENTE` | Aceita pela Central (temos protocolo), aguardando decisão. |
| `CONFIRMADA` | Confirmação da Central. Final. |
| `REJEITADA` | Rejeição da Central, com o motivo. Final. |
| `FALHA_ENVIO` | A Central respondeu 503, recusou a conexão ou estourou o timeout no envio. Pode ser reenviada com a mesma `Idempotency-Key` e libera as parcelas. |

Transições: criação → `PENDENTE` ou `FALHA_ENVIO`; `FALHA_ENVIO` → `PENDENTE` ou `FALHA_ENVIO` (reenvio); `PENDENTE` → `CONFIRMADA` ou `REJEITADA`. A passagem de `PENDENTE` para um estado final é uma atualização condicional, que só se aplica se a operação ainda estiver `PENDENTE`. Assim, consultas concorrentes registram a transição uma única vez.

## Falhas de integração

- Timeouts explícitos no cliente da Central: 2 s para conexão e 5 s para leitura.
- **Sem retry automático no `POST`**, porque a Central não é idempotente ([0005](./0005-idempotent-portability-requests.md)).
- Falha no envio: a operação vai para `FALHA_ENVIO`, e o frontend recebe `503` com o `id` da operação.
- 503 ou timeout ao consultar o status: a operação continua `PENDENTE`, e a tela mostra "não foi possível atualizar; tentando novamente".
- 404 do protocolo: a operação continua `PENDENTE`, com um aviso explícito. Isso só acontece se o simulador reiniciar, porque ele guarda as operações em memória. É uma limitação documentada.
- Todo erro chega ao frontend como `application/problem+json` (`ProblemDetail`), com uma mensagem legível.

## Trilha de eventos

A tabela `operacao_evento` é só de inserção e guarda operação, tipo, data/hora e detalhe. Os tipos são `SOLICITADA`, `ENVIADA`, `FALHA_ENVIO`, `CONFIRMADA` e `REJEITADA`. Cada evento é gravado na mesma transação da mudança de estado que o originou, e a tela da operação os mostra como linha do tempo.

## Opções consideradas

- **Hibernate Envers**: audita linhas inteiras e é pesado para registrar transições de estado.
- **Não persistir falhas de envio, só devolver o erro**: é mais simples, mas perde o registro da falha na trilha e impede reenviar a mesma operação.

## Consequências

- Com o polling feito pelo cliente ([0008](./0008-status-tracking-by-client-polling.md)), a data/hora de `CONFIRMADA` e `REJEITADA` é a de quando *observamos* a decisão, não a de quando a Central decidiu.
