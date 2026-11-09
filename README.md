# Portabilidade de Convênios

Solução fullstack (Java + React) para o [desafio técnico](./desafio-tecnico-fullstack-react-java-candidato-junior-pleno.md) de portabilidade de convênios de desconto em folha, integrada ao simulador da Central de Convênios fornecido.

## 1. Para o avaliador

Com a stack de pé (`docker compose up --build -d --wait`, banco limpo) em <http://localhost:8080>. O simulador tem estes beneficiários:

| Beneficiário | Parcelas | Resultado da operação |
|---|---|---|
| `BEN001` | `P001` e `P002` (`CONV01`, R$ 150,00 cada) | confirmada |
| `BEN002` | `P010` (`CONV02`, R$ 300,00) | rejeitada |
| `BEN_ERRO` | nenhuma | — |

1. **Confirmação:** autorize o `BEN001`, selecione a `P001`, preencha a conta de destino (por exemplo `001`, `1234`, `56789`, `0`) e envie. Cerca de 5 s depois, a operação fica "Confirmada", com a linha do tempo Solicitada → Enviada → Confirmada, e a `P001` deixa de ser elegível.
2. **Rejeição:** repita com o `BEN002` e a `P010`. A operação fica "Rejeitada", com o motivo dado pela Central de Convênios. A rejeição libera a `P010`, então dá para repetir.
3. **Falha de comunicação:** na agenda do `BEN001`, selecione a `P002` e preencha a conta, sem enviar. Rode `docker compose stop mock-central` e envie: a tela explica o erro e oferece "Tentar novamente". Nenhuma operação é criada, porque o backend relê a agenda na Central antes de gravá-la ([ADR-0006](./docs/adr/0006-eligibility-validated-against-central.md)). Rode `docker compose start mock-central` e tente de novo: o reenvio usa a mesma `Idempotency-Key` ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)) e a operação segue até "Confirmada".
4. **Histórico:** o link "Histórico", no topo, lista as operações enviadas, e cada linha abre a operação.
5. **API:** o Swagger UI em <http://localhost:8080/swagger-ui.html> permite chamar cada endpoint direto.

**Sobre o `BEN_ERRO`.** No simulador, ele só devolve 503 no envio da operação, mas a agenda dele é vazia. Como o backend valida as parcelas contra essa agenda ([ADR-0006](./docs/adr/0006-eligibility-validated-against-central.md)), não há o que enviar pela UI. Esse 503, que deixa a operação em `FALHA_ENVIO`, é coberto pelos testes de integração do backend, com o WireMock.

## 2. Visão geral

Um fluxo único em três telas, com o vocabulário do [glossário](./GLOSSARY.md):

1. **Autorização:** o beneficiário informa seu identificador e autoriza a consulta e a portabilidade.
2. **Agenda:** as parcelas vêm da Central de Convênios, com a elegibilidade de cada uma. O usuário seleciona as parcelas e informa a conta de destino.
3. **Operação:** a tela acompanha a operação até a confirmação ou a rejeição, com a linha do tempo de eventos.

O link "Histórico" lista as 50 operações mais recentes ([ADR-0013](./docs/adr/0013-operation-history-from-local-database.md)). A interface usa a identidade visual da Cielo, com um aviso de que é um projeto de avaliação sem vínculo oficial ([ADR-0012](./docs/adr/0012-cielo-visual-identity.md), [`DESIGN.md`](./DESIGN.md)).

- **Backend** (Java 25, Spring Boot 4.1, PostgreSQL): o único que fala com a Central de Convênios, com requisições assinadas por HMAC, timeouts explícitos e idempotência garantida no banco.
- **Frontend** (React, TypeScript, Vite): fala só com o backend e acompanha a operação por polling.
- **Simulador** (`mock-central/mock-central.js`): o arquivo do briefing, sem alterações.

Cada requisito do briefing, com a interpretação adotada, está no [catálogo de requisitos](./docs/requirements.md), e as decisões estão em [`docs/adr/`](./docs/adr/).

## 3. Pré-requisitos

- **Para rodar tudo:** Docker com Docker Compose v2. Nada mais.
- **Para rodar as partes isoladamente:** JDK 25 (o backend traz o Maven Wrapper), Node 24 e pnpm, via `corepack enable`.
- **Para os testes:** Docker também no backend (Testcontainers) e no E2E (a stack do compose).

## 4. Como executar

### Tudo com Docker Compose

Na raiz do repositório:

```sh
docker compose up --build
```

A aplicação fica em <http://localhost:8080> e o Swagger UI em <http://localhost:8080/swagger-ui.html>. O primeiro build leva alguns minutos. O compose sobe o frontend (nginx, que repassa `/api` ao backend), o backend, o PostgreSQL (porta 5432) e o simulador (porta 4000). Para parar: `docker compose down`, ou `docker compose down -v` para apagar também os dados.

O segredo HMAC versionado no `compose.yaml` é só de desenvolvimento. Fora do compose, `CENTRAL_HMAC_SECRET` não tem valor padrão e o backend não sobe sem ela ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).

### Backend, frontend e simulador separados

Com a stack completa parada (o backend local usa a porta 8080), suba só o banco e o simulador, e rode backend e frontend na máquina ([ADR-0003](./docs/adr/0003-postgresql-and-docker-compose.md)):

```sh
# terminal 1: banco e simulador
docker compose up postgres mock-central

# terminal 2: backend em http://localhost:8080 (as migrações do Flyway rodam na subida)
cd backend
CENTRAL_HMAC_SECRET=dev-somente-local-nao-usar-em-producao ./mvnw spring-boot:run

# terminal 3: frontend em http://localhost:5173, que repassa /api para o backend
cd frontend
pnpm install
pnpm dev
```

- **Windows (PowerShell):** o backend sobe com `$env:CENTRAL_HMAC_SECRET="dev-somente-local-nao-usar-em-producao"; .\mvnw.cmd spring-boot:run`.
- **Outras variáveis:** `CENTRAL_URL`, `DB_URL`, `DB_USER` e `DB_PASSWORD` já têm como padrão os endereços do compose.
- **Simulador fora do Docker:** `node mock-central/mock-central.js`, na porta 4000, só com módulos nativos do Node.

## 5. Como testar

A estratégia está na [ADR-0010](./docs/adr/0010-test-strategy.md). A CI (`.github/workflows/ci.yml`) roda as três suítes a cada push e pull request.

**Backend:** integração da API com PostgreSQL real (Testcontainers) e a Central de Convênios simulada pelo WireMock: 503, timeout, 404, concorrência e assinatura HMAC.

```sh
cd backend
./mvnw verify
```

**Frontend:** testes de componente com Vitest, Testing Library e MSW.

```sh
cd frontend
pnpm install
pnpm lint
pnpm test
```

**E2E:** Playwright no navegador contra a stack do compose: autorização e agenda, `BEN001` confirmado, `BEN002` rejeitado e duplo clique no envio.

```sh
docker compose down -v               # banco limpo: uma parcela confirmada fica travada para sempre
docker compose up --build -d --wait
cd frontend
pnpm install
pnpm exec playwright install chromium
pnpm e2e
```

## 6. Decisões e porquês

Um resumo. Cada ADR traz o contexto e as opções descartadas.

| Decisão | Por quê | ADR |
|---|---|---|
| Domínio em português; termos técnicos em inglês | É o vocabulário do contrato da Central de Convênios e do glossário | [0001](./docs/adr/0001-portuguese-domain-language.md) |
| Só o backend fala com a Central de Convênios; Spring Boot 4.1 com Java 25 | Autorização, deduplicação, HMAC e trilha de eventos não se sustentam se o navegador chamar a Central | [0002](./docs/adr/0002-backend-as-sole-central-integrator.md) |
| PostgreSQL com Flyway, tudo num `docker compose up` | A deduplicação depende de `UNIQUE` e de concorrência reais, que o H2 só imita; o avaliador só precisa de Docker | [0003](./docs/adr/0003-postgresql-and-docker-compose.md) |
| Fluxo único, sem login, com a autorização exigida localmente (`403`) | O briefing pede uma captura simples da autorização | [0004](./docs/adr/0004-single-flow-without-authentication.md) |
| Idempotência em duas camadas, garantidas no banco (detalhe abaixo) | A Central de Convênios não é idempotente: cada envio gera um protocolo novo | [0005](./docs/adr/0005-idempotent-portability-requests.md) |
| Elegibilidade relida na Central de Convênios ao criar a operação | O frontend não é fonte de verdade | [0006](./docs/adr/0006-eligibility-validated-against-central.md) |
| Estados explícitos, timeouts de 2 s e 5 s, nenhum retry automático no envio, erros em `problem+json` | Cada falha da Central vira um estado e uma mensagem, sem reenvio às cegas | [0007](./docs/adr/0007-operation-states-and-event-trail.md) |
| Polling do frontend no ritmo do `Retry-After` | A Central só oferece polling, e a decisão leva cerca de 5 s | [0008](./docs/adr/0008-status-tracking-by-client-polling.md) |
| Requisições à Central assinadas com HMAC-SHA256 | Integridade da mensagem, com timestamp contra replay | [0009](./docs/adr/0009-hmac-signed-central-requests.md) |
| Testes nas fronteiras: API HTTP, rede do frontend e navegador | Os cenários de erro ficam na integração, onde o WireMock é determinístico | [0010](./docs/adr/0010-test-strategy.md) |
| TanStack Router e Query, React Hook Form + Zod, Tailwind + shadcn/ui | Rotas tipadas, polling e estado do envio prontos | [0011](./docs/adr/0011-frontend-stack.md) |
| Identidade visual da Cielo, só tema claro | A marca aparece com precisão, não com volume | [0012](./docs/adr/0012-cielo-visual-identity.md) |
| Histórico lido só do banco | Lista rápida e independente da Central | [0013](./docs/adr/0013-operation-history-from-local-database.md) |

**Como a duplicidade é evitada** ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)):

1. **Chave de idempotência.** O frontend gera uma `Idempotency-Key` por intenção e a reutiliza em qualquer reenvio. O backend grava a chave com `UNIQUE`, junto com o hash do corpo: mesma chave e mesmo corpo devolvem a mesma operação, sem nova chamada à Central de Convênios; mesma chave com outro corpo dá `422`. Uma requisição concorrente com a mesma chave espera o commit da primeira no índice único e recebe a operação existente.
2. **Uma parcela, uma operação ativa.** Um índice único parcial impede que uma parcela esteja em duas operações `PENDENTE` ou `CONFIRMADA`, mesmo vindas de abas com chaves diferentes; a segunda recebe `409`. `REJEITADA` e `FALHA_ENVIO` liberam as parcelas.
3. **No frontend**, o botão de envio fica desabilitado enquanto a requisição está em andamento.

O envio duplo concorrente é coberto pelos testes de integração, e o duplo clique pelo E2E.

## 7. Bibliotecas e justificativas

A assinatura HMAC usa só `javax.crypto.Mac`, sem biblioteca adicional.

**Backend** ([`backend/pom.xml`](./backend/pom.xml), versões do Spring Boot 4.1.2, salvo indicação):

| Biblioteca | Justificativa |
|---|---|
| `spring-boot-starter-webmvc` + `validation` | API REST, `ProblemDetail` e Bean Validation |
| `spring-boot-starter-restclient` | `RestClient` da Central de Convênios, com os timeouts por propriedade |
| `spring-boot-starter-data-jpa` + `postgresql` | Persistência de autorizações, operações e eventos |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Schema versionado, incluindo as constraints da deduplicação |
| `springdoc-openapi-starter-webmvc-ui` 3.2.0 | OpenAPI gerado do código e Swagger UI; a linha 3.x é a do Spring Boot 4 |
| `spring-boot-starter-webmvc-test` + Testcontainers (teste) | API HTTP exercitada com PostgreSQL real, na mesma imagem do compose |
| `wiremock-standalone` 3.13.2 (teste) | Central simulada de forma determinística e conferência do HMAC; a versão standalone evita conflito de classpath com o Spring Boot 4 |

**Frontend** ([`frontend/package.json`](./frontend/package.json), com pnpm):

| Biblioteca | Justificativa |
|---|---|
| `@tanstack/react-router` | Uma rota tipada por passo do fluxo |
| `@tanstack/react-query` | Polling com `refetchInterval`, estado do envio e cancelamento com `AbortSignal` |
| `react-hook-form` + `@hookform/resolvers` + `zod` | Validação do formato da conta de destino declarada num schema |
| `tailwindcss` + `shadcn` sobre `@base-ui/react` | Estilos utilitários e componentes sobre primitivas acessíveis |
| `lucide-react` + `@fontsource-variable/nunito-sans` | Ícones e a fonte da identidade visual, empacotada sem CDN |
| `vitest` + Testing Library + `msw` (dev) | Testes de componente pelo que o usuário vê, com a rede interceptada |
| `@playwright/test` (dev) | E2E no navegador contra a stack do compose |

O resto é ferramental: Vite e TypeScript no build, ESLint (com as regras dos hooks do React) e Prettier (com a ordem das classes do Tailwind) na qualidade. `class-variance-authority`, `cn` e `tw-animate-css` vêm com os componentes do shadcn/ui.

## 8. Diferenciais implementados

Os quatro diferenciais do briefing ([D-01 a D-04](./docs/requirements.md#diferenciais)), além do Swagger UI e da CI com as três suítes:

- **HMAC:** toda requisição à Central de Convênios leva `X-Assinatura` e `X-Timestamp`, e os testes conferem a assinatura recebida pelo WireMock ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).
- **Trilha de eventos:** cada mudança de estado grava um evento na mesma transação, e a tela mostra a linha do tempo ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)).
- **Testes de cenários de erro:** 503, 404, timeout, envio duplo concorrente, `409` e `422` na integração, e o duplo clique no E2E ([ADR-0010](./docs/adr/0010-test-strategy.md)).
- **UX:** envio desabilitado durante a requisição, parcelas inelegíveis com o motivo, indicação de espera e erros com a ação possível.

## 9. Limitações conhecidas

- **Protocolo órfão em timeout.** Um timeout no envio é ambíguo, porque a Central de Convênios pode ter aceitado. A operação vai para `FALHA_ENVIO`, e o reenvio pode deixar um protocolo órfão na Central. Não há solução sem idempotência do lado dela ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).
- **Parcelas travadas se o simulador reiniciar.** O simulador guarda as operações em memória. Depois de reiniciado, uma operação `PENDENTE` recebe 404 e continua `PENDENTE`, com aviso, e as parcelas dela ficam travadas. Na demonstração, só `docker compose down -v` resolve ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)).
- **Data/hora dos eventos finais.** É a de quando o polling observou a decisão, não a de quando a Central decidiu. Sem ninguém na tela, a operação fica `PENDENTE` no banco ([ADR-0008](./docs/adr/0008-status-tracking-by-client-polling.md)).
- **Conexão do banco presa durante as chamadas à Central.** O envio e a consulta chamam a Central dentro da transação, por até cerca de 14 s no envio e 7 s na consulta. Com a Central lenta e muitas telas abertas, o pool de conexões pode se esgotar. A evolução é chamar a Central fora da transação ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).
- **Conta de destino** validada só no formato, sem verificar se existe ([RF-03](./docs/requirements.md#requisitos-funcionais)).

## 10. Fora de escopo e próximos passos

Escolhas de escopo, a partir de ["Fora de escopo"](./docs/requirements.md#fora-de-escopo) no catálogo:

- **Autenticação e ciclo de vida da autorização.** Não há login. **Risco aceito:** qualquer pessoa com acesso à tela pode registrar a autorização de qualquer `beneficiarioId`. Próximos passos: Spring Security com OAuth2/OIDC, telas separadas para o beneficiário e o operador, identificação real do beneficiário, e expiração e revogação da autorização ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)).
- **Acompanhamento no backend:** um job consultando as operações pendentes e, depois, SSE ou WebSocket para levar as mudanças à tela ([ADR-0008](./docs/adr/0008-status-tracking-by-client-polling.md)).
- **Verificação do HMAC pela Central de Convênios**, que exigiria alterar o simulador ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).
- **Recuperação de protocolos órfãos** após timeout no envio, que depende de idempotência do lado da Central ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).

## 11. Como a IA foi usada

Usei o Claude Code (agente de linha de comando) do planejamento à entrega, com skills que conduzem cada etapa. As decisões de escopo e de arquitetura e a aprovação de cada PR foram minhas; o agente redigiu os documentos, escreveu o código e os testes e fez as revisões.

1. **Planejamento.** Com a skill `grilling`, o agente me questionou sobre o briefing até cada ponto aberto virar uma decisão. A skill `domain-modeling` registrou essas decisões nos [ADRs](./docs/adr/) e no [GLOSSARY.md](./GLOSSARY.md), e o [catálogo de requisitos](./docs/requirements.md) liga cada requisito ao ADR que o atende (PR #1, só documentação).
2. **PRDs e tickets.** Cada fatia vertical virou um PRD (#2, #3, #4) e tickets como sub-issues no GitHub, com as dependências entre eles registradas como "blocked by" nativo (por exemplo, #15 depende de #11 e #12).
3. **Implementação.** Para cada PRD, uma branch de integração; para cada ticket, um subagente numa worktree isolada, trabalhando com TDD (skill `tdd`) nas três camadas de teste do [ADR-0010](./docs/adr/0010-test-strategy.md). Para APIs de bibliotecas recentes (Spring Boot 4, TanStack), o agente consultou a documentação atual pelo Context7.
4. **Revisão.** As PRs de código passaram por revisão antes do merge. Na PR #10 a revisão gerou correções (registro serializado por beneficiário, validação do `beneficiarioId`, erros inesperados da Central como `502`) e um refactor guiado pelas skills `clean-code` e `solid`. Na PR #17, uma revisão de esforço alto feita pelo agente apontou 10 problemas: seis foram corrigidos na própria PR.
5. **Onde a IA não teve a palavra final.** Parte dos apontamentos da PR #17 foi recusada porque contrariava decisões já registradas: a chamada à Central dentro da transação ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)), a falta de autorização no `GET` da operação ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)) e o 4xx da Central no envio sem `FALHA_ENVIO` ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)). Os ADRs serviram de critério para aceitar ou recusar o que o agente propunha.

As skills versionadas no repositório estão em [`.agents/skills/`](./.agents/skills/), com a origem em [`skills-lock.json`](./skills-lock.json); as demais (`grilling`, `domain-modeling`, revisão de código) vêm da minha instalação local do Claude Code.
