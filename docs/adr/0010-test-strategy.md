# Estratégia de testes

- **Backend, unitários:** transições de estado, elegibilidade, hash do corpo para idempotência e assinatura HMAC.
- **Backend, integração:** `@SpringBootTest` com **Testcontainers** (Postgres) e **WireMock** no lugar da Central. Cobrem:
  - respostas da Central: 201/202, 204 com `Retry-After`, 200 `CONFIRMADA`/`REJEITADA`, 503, 404 e timeout;
  - deduplicação e conflitos: envio duplo concorrente, reenvio com a mesma chave, `422` e `409`.
- **Frontend:** **Vitest + React Testing Library + MSW** para os fluxos principais e as mensagens de erro.
- **E2E:** **Playwright** em `frontend/e2e/`, contra a stack completa do compose com o simulador real:
  - `BEN001`: confirmação, incluindo a espera e a linha do tempo;
  - `BEN002`: rejeição com o motivo;
  - duplo clique no envio: uma única operação criada.
- **CI:** GitHub Actions com três jobs (backend, frontend e E2E sobre o compose), rodando em todo push e PR.

## Opções consideradas

- **Simulador real nos testes de integração:** descartado. O WireMock é determinístico, não espera os 5 s e consegue simular timeout. O simulador real é exercitado pelo E2E.
- **Cenários de erro no E2E:** descartados. O `BEN_ERRO` não é alcançável pela UI ([0006](./0006-eligibility-validated-against-central.md)), e derrubar containers no meio do teste deixaria o E2E frágil. Esses cenários ficam na integração.
