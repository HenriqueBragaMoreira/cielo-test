# Backend como único integrador da Central de Convênios

O backend é Spring Boot 4.1 com Java 25, e o frontend é React + TypeScript com Vite, num monorepo (`backend/`, `frontend/`, `mock-central/`, `docs/`). O frontend fala apenas com o nosso backend, sob `/api`, e só o backend chama a Central. Isso é pré-requisito para a regra de autorização local ([0004](./0004-single-flow-without-authentication.md)), a deduplicação ([0005](./0005-idempotent-portability-requests.md)), a assinatura HMAC ([0009](./0009-hmac-signed-central-requests.md)) e a trilha de eventos ([0007](./0007-operation-states-and-event-trail.md)). Nenhuma delas se sustenta se o navegador chamar a Central diretamente.

## Organização do backend

Os pacotes são organizados por funcionalidade:
- `autorizacao/`, `agenda/` e `portabilidade/`: um pacote por fluxo;
- `central/`: o único adaptador HTTP para a Central (`RestClient`, timeouts, HMAC e tradução de erros);
- `shared/`: tratamento de erros (`ProblemDetail`) e configuração.

A organização por camada (`controller/`, `service/`, `repository/`) foi descartada porque espalha cada fluxo por várias pastas.

## Opções consideradas

- **Spring Boot 3.5 / Java 21**: descartado porque a linha 3.x saiu do suporte open source em 2026.
- **Frontend chamando a Central diretamente**: descartado pelos motivos acima.

## Consequências

- O Spring Boot 4 traz Jackson 3, starters modularizados, Testcontainers 2 e springdoc 3.x.
- `mock-central/mock-central.js` é uma cópia literal do briefing e nunca deve ser alterada, porque o avaliador roda a própria cópia.
