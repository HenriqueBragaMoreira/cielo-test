# Portabilidade de Convênios

Solução fullstack (Java + React) para o [desafio técnico](./desafio-tecnico-fullstack-react-java-candidato-junior-pleno.md) de portabilidade de convênios de desconto em folha, integrada ao simulador da Central de Convênios fornecido.

## Para o avaliador

Roteiro curto, com a stack de pé (`docker compose up --build -d --wait`, banco limpo) em <http://localhost:8080>:

1. **Confirmação:** autorize o `BEN001`, selecione a `P001`, preencha a conta de destino (por exemplo `001`, `1234`, `56789`, `0`) e envie. Cerca de 5 s depois, a operação fica "Confirmada".
2. **Rejeição:** repita com o `BEN002` e a `P010`. A operação fica "Rejeitada", com o motivo dado pela Central de Convênios.
3. **Falha de comunicação:** na agenda do `BEN001`, selecione a `P002` e preencha a conta, sem enviar. Rode `docker compose stop mock-central` e envie: a tela explica o erro e oferece "Tentar novamente". Rode `docker compose start mock-central` e tente de novo: o reenvio usa a mesma `Idempotency-Key` e a operação segue até "Confirmada".
4. **Histórico:** o link "Histórico", no topo, lista as operações enviadas, e cada linha abre a operação.
5. **API:** o Swagger UI em <http://localhost:8080/swagger-ui.html> permite chamar cada endpoint direto.

**Sobre o `BEN_ERRO`.** No simulador, a agenda dele é vazia. Como o backend valida as parcelas contra essa agenda ([ADR-0006](./docs/adr/0006-eligibility-validated-against-central.md)), não há o que enviar, e o 503 do envio não aparece pela UI. A falha de comunicação fica demonstrada no passo 3, e o 503 do `BEN_ERRO` é coberto pelos testes de integração do backend. O passo a passo completo está no [roteiro de demonstração](#5-roteiro-de-demonstração).

## 1. Visão geral

**Problema.** Uma nova credora quer assumir o direito de receber parcelas de convênios de desconto em folha que hoje pertencem a outro credor. Para isso precisa da autorização do beneficiário, consultar a agenda de parcelas na Central de Convênios, pedir a portabilidade das parcelas escolhidas e acompanhar a decisão, que não é imediata. Reenvios (duplo clique, retry após falha de conexão) não podem criar duas operações para a mesma intenção.

**Solução.** Um fluxo único em três telas:

1. **Autorização**: o beneficiário informa seu identificador e autoriza a consulta e a portabilidade; a autorização é registrada na Central de Convênios.
2. **Agenda**: as parcelas vêm da Central de Convênios, com a elegibilidade de cada uma. O usuário seleciona as parcelas e informa a conta de recebimento de destino.
3. **Operação de portabilidade**: a tela acompanha a operação até a confirmação ou a rejeição, com a linha do tempo de eventos e os erros de integração explicados.

A interface usa a identidade visual da Cielo (wordmark recriado em SVG, azul-marinho e ciano da marca, só tema claro), com um aviso de que é um projeto de avaliação sem vínculo oficial com a Cielo ([ADR-0012](./docs/adr/0012-cielo-visual-identity.md)); o sistema visual está descrito em [`DESIGN.md`](./DESIGN.md). O link "Histórico", no topo de todas as telas, lista as 50 operações mais recentes lidas só do banco, sem consultar a Central de Convênios, e cada linha abre a tela da operação ([ADR-0013](./docs/adr/0013-operation-history-from-local-database.md)).

Peças:

- **Backend** (Java 25, Spring Boot 4.1, PostgreSQL): é o único que fala com a Central de Convênios, com requisições assinadas por HMAC, timeouts explícitos e idempotência garantida no banco.
- **Frontend** (React, TypeScript, Vite): fala só com o backend e acompanha a operação por polling.
- **Simulador** (`mock-central/mock-central.js`): o arquivo do briefing, sem alterações.

O vocabulário segue o [glossário](./GLOSSARY.md). Cada requisito do briefing, com a interpretação adotada, está no [catálogo de requisitos](./docs/requirements.md), e as decisões estão em [`docs/adr/`](./docs/adr/).

## 2. Pré-requisitos

- **Para rodar tudo:** Docker com Docker Compose v2. Nada mais.
- **Para rodar as partes isoladamente:**
  - JDK 25 (o backend traz o Maven Wrapper, não precisa instalar o Maven);
  - Node 24;
  - pnpm, via `corepack enable` (a CI usa o pnpm 12).
- **Para os testes:** Docker também no backend (Testcontainers) e no E2E (a stack do compose).

## 3. Como executar

### Tudo com Docker Compose

Na raiz do repositório:

```sh
docker compose up --build
```

- Aplicação: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

O primeiro build leva alguns minutos (dependências do Maven e do pnpm); os seguintes são rápidos. Com `docker compose up --build -d --wait` o comando volta quando a aplicação está pronta.

O compose sobe quatro serviços:

| Serviço | Porta no host | O quê |
|---|---|---|
| `frontend` | 8080 | nginx com o build do React; repassa `/api` e o Swagger para o backend |
| `backend` | — | Spring Boot, acessado pelo nginx |
| `postgres` | 5432 | banco da aplicação |
| `mock-central` | 4000 | simulador da Central de Convênios (`node:24-alpine`) |

O segredo HMAC versionado no `compose.yaml` é só de desenvolvimento ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).

Para parar: `docker compose down`. Para também apagar os dados: `docker compose down -v`.

### Modo de desenvolvimento

Com a stack completa parada (o backend local usa a porta 8080), o compose sobe só o banco e o simulador, e backend e frontend rodam na máquina ([ADR-0003](./docs/adr/0003-postgresql-and-docker-compose.md)):

```sh
docker compose up postgres mock-central
```

Depois, em dois terminais, o backend e o frontend como abaixo. A aplicação fica em <http://localhost:5173>.

### Backend

```sh
cd backend
CENTRAL_HMAC_SECRET=dev-somente-local-nao-usar-em-producao ./mvnw spring-boot:run
```

No Windows (PowerShell): `$env:CENTRAL_HMAC_SECRET="dev-somente-local-nao-usar-em-producao"; .\mvnw.cmd spring-boot:run`.

Sobe em <http://localhost:8080> (Swagger UI em <http://localhost:8080/swagger-ui.html>). Variáveis de ambiente:

| Variável | Padrão |
|---|---|
| `CENTRAL_HMAC_SECRET` | nenhum: obrigatória, o backend não sobe sem ela |
| `CENTRAL_URL` | `http://localhost:4000` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/portabilidade` |
| `DB_USER` / `DB_PASSWORD` | `portabilidade` / `portabilidade` |

Precisa do banco e do simulador de pé (`docker compose up postgres mock-central`). As migrações do Flyway rodam na subida.

### Frontend

```sh
cd frontend
pnpm install
pnpm dev
```

Sobe em <http://localhost:5173> e repassa `/api` para o backend em `http://localhost:8080`.

### Simulador

```sh
node mock-central/mock-central.js
```

Sobe em <http://localhost:4000>, só com módulos nativos do Node. É o arquivo do briefing, sem alterações ([ADR-0002](./docs/adr/0002-backend-as-sole-central-integrator.md)).

## 4. Como testar

A estratégia está na [ADR-0010](./docs/adr/0010-test-strategy.md). A CI (`.github/workflows/ci.yml`) roda as três suítes a cada push e pull request.

**Backend**: testes unitários e de integração da API, com PostgreSQL real (Testcontainers) e a Central de Convênios simulada pelo WireMock (503, timeout, 404, concorrência, assinatura HMAC). Precisa de JDK 25 e Docker:

```sh
cd backend
./mvnw verify
```

**Frontend**: testes de componente com Vitest, Testing Library e MSW:

```sh
cd frontend
pnpm install
pnpm lint
pnpm test
```

**E2E**: Playwright no navegador contra a stack do compose: autorização e agenda, `BEN001` confirmado, `BEN002` rejeitado e duplo clique no envio.

```sh
docker compose down -v               # banco limpo: uma parcela confirmada fica travada para sempre
docker compose up --build -d --wait
cd frontend
pnpm install
pnpm exec playwright install chromium
pnpm e2e
```

## 5. Roteiro de demonstração

Com a stack de pé (`docker compose up --build`) e o banco limpo, em <http://localhost:8080>. O simulador tem estes beneficiários:

| Beneficiário | Parcelas | Resultado da operação |
|---|---|---|
| `BEN001` | `P001` e `P002` (`CONV01`, R$ 150,00 cada) | confirmada |
| `BEN002` | `P010` (`CONV02`, R$ 300,00) | rejeitada |
| `BEN_ERRO` | nenhuma | — |

**`BEN001` confirma.**

1. Informe `BEN001`, marque "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas" e clique em "Registrar autorização".
2. Na agenda, selecione `P001`, preencha a conta de destino (por exemplo banco `001`, agência `1234`, conta `56789`, dígito `0`) e clique em "Enviar portabilidade".
3. A tela da operação mostra "Aguardando a decisão da Central de Convênios…" e, alguns segundos depois (a Central de Convênios decide cerca de 5 s após o envio), "Operação confirmada", com a linha do tempo Solicitada → Enviada → Confirmada. A `P001` deixa de ser elegível na agenda.

**`BEN002` rejeita.** Repita com `BEN002` e a parcela `P010`. O resultado é "Operação rejeitada", com o motivo "Beneficiário atingiu o limite de margem consignável". A rejeição libera a `P010`, então este caso pode ser repetido.

**Falha de comunicação com a Central de Convênios.** Desligue o simulador no meio do fluxo:

1. Autorize o `BEN001` e, na agenda, selecione a `P002` e preencha a conta de destino, sem enviar.
2. Em outro terminal: `docker compose stop mock-central`.
3. Clique em "Enviar portabilidade". A tela mostra "A Central de Convênios não respondeu. Tente novamente mais tarde." e o botão "Tentar novamente". Nenhuma operação é criada: antes de gravá-la, o backend relê a agenda na Central de Convênios ([ADR-0006](./docs/adr/0006-eligibility-validated-against-central.md)), e essa leitura já falha.
4. `docker compose start mock-central` e clique em "Tentar novamente": o pedido é reenviado com a mesma `Idempotency-Key` ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)) e a operação segue até "Operação confirmada".

Com o simulador desligado, registrar uma autorização e abrir a agenda mostram a mesma mensagem, a agenda com o botão "Tentar novamente". O estado `FALHA_ENVIO`, em que a operação já foi gravada e pode ser reenviada, só aparece quando a Central de Convênios responde à leitura da agenda e falha no envio da operação. Esse caso não dá para provocar pela UI e é coberto pelos testes de integração do backend (veja o `BEN_ERRO` abaixo).

**Por que não o `BEN_ERRO`.** No simulador, o `BEN_ERRO` só devolve 503 no envio da operação, mas sua agenda é vazia. Como o backend valida as parcelas contra a agenda da Central de Convênios ([ADR-0006](./docs/adr/0006-eligibility-validated-against-central.md)), não há parcela para selecionar e o envio nunca acontece pela UI. Esse 503 é coberto pelos testes de integração do backend, com o WireMock.

## 6. Decisões e porquês

Um resumo; cada ADR traz o contexto e as opções descartadas. O [catálogo de requisitos](./docs/requirements.md) liga cada requisito do briefing às decisões.

| Decisão | Por quê | ADR |
|---|---|---|
| Domínio em português no código, na API e na documentação; termos técnicos em inglês | É o vocabulário do contrato da Central de Convênios e do [glossário](./GLOSSARY.md), sem tradução mental campo a campo | [0001](./docs/adr/0001-portuguese-domain-language.md) |
| Só o backend fala com a Central de Convênios; pacotes por funcionalidade, com um único adaptador `central/`; Spring Boot 4.1 com Java 25 | Autorização local, deduplicação, HMAC e trilha de eventos não se sustentam se o navegador chamar a Central de Convênios. A linha 3.x do Spring Boot saiu do suporte open source em 2026 | [0002](./docs/adr/0002-backend-as-sole-central-integrator.md) |
| PostgreSQL com Flyway, tudo num `docker compose up`, com o nginx servindo o frontend e a API na mesma origem | A deduplicação depende de `UNIQUE` e de concorrência confiáveis, que o H2 só imita; o avaliador só precisa de Docker; sem CORS | [0003](./docs/adr/0003-postgresql-and-docker-compose.md) |
| Fluxo único, sem login, com a autorização exigida localmente (`403` sem autorização registrada) | O briefing pede uma captura simples da autorização, e a Central de Convênios não aplica a regra de beneficiário autorizado | [0004](./docs/adr/0004-single-flow-without-authentication.md) |
| Idempotência em duas camadas, ambas garantidas no banco (detalhe abaixo) | A Central de Convênios não é idempotente: cada envio gera um protocolo novo | [0005](./docs/adr/0005-idempotent-portability-requests.md) |
| Elegibilidade = parcela `ABERTA` na agenda da Central de Convênios e fora de operação ativa; o backend relê a agenda ao criar a operação | O frontend não é fonte de verdade, e o simulador nunca muda o status de uma parcela portada | [0006](./docs/adr/0006-eligibility-validated-against-central.md) |
| Estados `PENDENTE`, `CONFIRMADA`, `REJEITADA` e `FALHA_ENVIO`; timeouts de 2 s (conexão) e 5 s (leitura); nenhum retry automático no envio; erros em `application/problem+json` | Cada resposta da Central de Convênios (503, 404, timeout, conexão recusada) vira um estado e uma mensagem explícitos, sem reenvio às cegas | [0007](./docs/adr/0007-operation-states-and-event-trail.md) |
| Acompanhamento por polling do frontend no ritmo do `Retry-After` (2 s), com botão "Atualizar" | A Central de Convênios só oferece polling; com um operador e uma espera de cerca de 5 s, é o mais simples. A evolução é job no backend e depois SSE | [0008](./docs/adr/0008-status-tracking-by-client-polling.md) |
| Toda requisição à Central de Convênios assinada com HMAC-SHA256, segredo em `CENTRAL_HMAC_SECRET` | Integridade da mensagem (diferencial do briefing), com timestamp na assinatura contra replay | [0009](./docs/adr/0009-hmac-signed-central-requests.md) |
| Testes nas costuras mais altas: API HTTP do backend, rede do frontend e navegador | Uma suíte por fronteira real do sistema; os cenários de erro ficam na integração, onde o WireMock é determinístico e simula timeout | [0010](./docs/adr/0010-test-strategy.md) |
| TanStack Router e Query, React Hook Form + Zod, Tailwind + shadcn/ui sobre Base UI, pnpm | Rotas tipadas, polling e estado de envio prontos, validação de formato da conta de recebimento de destino | [0011](./docs/adr/0011-frontend-stack.md) |
| Identidade visual da Cielo, só tema claro, com a régua da agenda como assinatura | O avaliador é da Cielo; a marca aparece com precisão (ciano só na seleção, na ação principal e no passo atual), não com volume | [0012](./docs/adr/0012-cielo-visual-identity.md) |
| Histórico de operações lido só do banco, as 50 mais recentes, sem consultar a Central de Convênios | Lista rápida e independente da Central; quem atualiza uma pendente continua sendo a tela da operação | [0013](./docs/adr/0013-operation-history-from-local-database.md) |

**Como a duplicidade é evitada** ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)):

1. **Chave de idempotência.** O frontend gera uma `Idempotency-Key` por intenção e a reutiliza em qualquer reenvio. O backend grava a chave com `UNIQUE`, junto com o hash do corpo: mesma chave e mesmo corpo devolvem a mesma operação, sem nova chamada à Central de Convênios; mesma chave com outro corpo dá `422`. Uma requisição concorrente com a mesma chave espera o commit da primeira no índice único e recebe a operação existente.
2. **Uma parcela, uma operação ativa.** Um índice único parcial impede que uma parcela esteja em duas operações `PENDENTE` ou `CONFIRMADA`, mesmo vindas de abas com chaves diferentes; a segunda recebe `409`. `REJEITADA` e `FALHA_ENVIO` liberam as parcelas.
3. **No frontend**, o botão de envio fica desabilitado enquanto a requisição está em andamento.

O envio duplo concorrente é coberto pelos testes de integração, e o duplo clique pelo E2E.

**Segredo HMAC.** O valor de `CENTRAL_HMAC_SECRET` versionado no `compose.yaml` (`dev-somente-local-nao-usar-em-producao`) é **só de desenvolvimento**. Um segredo real nunca seria versionado: viria do ambiente de execução. Fora do compose, a variável não tem valor padrão e o backend não sobe sem ela ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).

## 7. Bibliotecas e justificativas

Só bibliotecas externas ao Java, ao Node e ao próprio React. A assinatura HMAC usa apenas `javax.crypto.Mac`, sem biblioteca adicional.

**Backend** ([`backend/pom.xml`](./backend/pom.xml), Spring Boot 4.1.2; versões gerenciadas pelo Spring Boot, salvo indicação):

| Biblioteca | Justificativa |
|---|---|
| `spring-boot-starter-webmvc` | API REST e `ProblemDetail` para os erros em `application/problem+json` |
| `spring-boot-starter-validation` | Bean Validation dos corpos das requisições |
| `spring-boot-starter-restclient` | `RestClient` do adaptador da Central de Convênios, com os timeouts configurados por propriedade |
| `spring-boot-starter-data-jpa` | Persistência de autorizações, operações e eventos |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Schema versionado, incluindo as constraints da deduplicação |
| `postgresql` | Driver JDBC do PostgreSQL |
| `springdoc-openapi-starter-webmvc-ui` 3.2.0 | OpenAPI gerado do código e Swagger UI em `/swagger-ui.html`; a linha 3.x é a compatível com o Spring Boot 4 |
| `spring-boot-starter-webmvc-test` (teste) | `RestTestClient` e o suporte de teste do Spring Boot para exercitar a API HTTP |
| `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` (teste) | PostgreSQL real nos testes, com a mesma imagem do compose |
| `wiremock-standalone` 3.13.2 (teste) | Central de Convênios simulada de forma determinística (503, 404, timeout) e conferência da assinatura HMAC recebida; a versão standalone tem Jetty e Jackson sombreados, sem conflito com o classpath do Spring Boot 4 |

**Frontend** ([`frontend/package.json`](./frontend/package.json), gerenciado com pnpm):

| Biblioteca | Justificativa |
|---|---|
| `@tanstack/react-router` | Uma rota tipada por passo do fluxo, com parâmetros tipados |
| `@tanstack/react-query` | `refetchInterval` para o polling, `isPending` para desabilitar o envio e cancelamento com `AbortSignal` |
| `react-hook-form` + `@hookform/resolvers` + `zod` | Formulários com validação de formato da conta de recebimento de destino declarada num schema |
| `tailwindcss` + `@tailwindcss/vite` | Estilos utilitários, integrados ao build do Vite |
| `shadcn` + `@base-ui/react` | Componentes shadcn/ui copiados para o projeto sobre primitivas acessíveis do Base UI; o pacote `shadcn` fornece a CLI e o CSS base |
| `class-variance-authority` | Variantes tipadas dos componentes (ex.: o botão) |
| `cn` | Junção condicional de classes nos componentes de UI |
| `tw-animate-css` | Animações usadas pelos componentes shadcn/ui |
| `lucide-react` | Ícones |
| `@fontsource-variable/nunito-sans` | Fonte da identidade visual (Nunito Sans) empacotada no build, sem depender de CDN |
| `vite` + `@vitejs/plugin-react` (dev) | Servidor de desenvolvimento com proxy de `/api` e build de produção |
| `typescript` + `@types/*` (dev) | Tipagem de ponta a ponta |
| `vitest` + `jsdom` (dev) | Testes de componente rápidos, no mesmo pipeline do Vite |
| `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` (dev) | Testes pelo que o usuário vê e faz, não pela implementação |
| `msw` (dev) | Intercepta `/api` nos testes de componente, sem mockar o código da aplicação |
| `@playwright/test` (dev) | E2E no navegador contra a stack do compose |
| `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals` (dev) | Lint, incluindo as regras dos hooks do React |
| `prettier` + `prettier-plugin-tailwindcss` (dev) | Formatação e ordem consistente das classes do Tailwind |

## 8. Diferenciais implementados

Os quatro diferenciais do briefing ([D-01 a D-04](./docs/requirements.md#diferenciais)):

- **Integridade da mensagem (HMAC).** Toda requisição à Central de Convênios leva `X-Assinatura` (HMAC-SHA256 sobre timestamp, método, caminho e corpo) e `X-Timestamp`. Verificar cabe à Central de Convênios, e o simulador ignora os headers; os testes conferem a assinatura no que o WireMock recebeu ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).
- **Trilha de eventos.** Cada mudança de estado grava um evento (`SOLICITADA`, `ENVIADA`, `FALHA_ENVIO`, `CONFIRMADA`, `REJEITADA`) numa tabela só de inserção, na mesma transação; a tela mostra a linha do tempo ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)).
- **Testes de cenários de erro.** Testes de integração com WireMock para 503, 404, timeout, envio duplo concorrente, `409` e `422`, e E2E do duplo clique ([ADR-0010](./docs/adr/0010-test-strategy.md)).
- **UX.** Botão desabilitado durante o envio, parcelas inelegíveis desabilitadas com o motivo, indicação de espera, linha do tempo da operação e mensagens de erro com a ação possível ("Tentar novamente", "Atualizar").

Além disso: documentação OpenAPI com Swagger UI e CI no GitHub Actions com as três suítes.

## 9. Limitações conhecidas

- **Protocolo órfão em timeout.** Um timeout no envio é ambíguo: a Central de Convênios pode ter aceitado. A operação vai para `FALHA_ENVIO`, e o reenvio pode criar um segundo protocolo na Central de Convênios, que fica órfão. Não tem solução sem idempotência do lado dela ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).
- **404 depois de reiniciar o simulador.** O simulador guarda as operações em memória. Depois de reiniciá-lo, uma operação `PENDENTE` recebe 404 do protocolo: continua `PENDENTE`, com o aviso de protocolo não reconhecido, e a tela para o polling. As parcelas dela continuam travadas, e a aplicação não oferece como liberá-las; na demonstração, só `docker compose down -v` resolve ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)).
- **Data/hora dos eventos finais.** Como o acompanhamento é por polling do frontend, a data/hora de `CONFIRMADA` e `REJEITADA` na trilha é a de quando observamos a decisão, não a de quando a Central de Convênios decidiu. Uma operação sem ninguém olhando fica `PENDENTE` no banco até alguém abrir a tela ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md), [ADR-0008](./docs/adr/0008-status-tracking-by-client-polling.md)).
- **Conexão com o banco presa durante chamadas à Central de Convênios.** O envio relê a agenda e envia a operação dentro de uma única transação, e cada consulta de uma operação `PENDENTE` chama a Central de Convênios dentro da sua transação. Com 2 s de conexão e 5 s de leitura por chamada, uma conexão pode ficar presa por até cerca de 14 s no envio e 7 s na consulta; com a Central lenta e várias telas abertas, o pool de conexões pode se esgotar. É aceitável nesta escala, e a evolução é chamar a Central fora da transação ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).
- **Conta de recebimento de destino** validada só no formato, sem verificar se existe ([RF-03](./docs/requirements.md#requisitos-funcionais)).

## 10. Fora de escopo e próximos passos

Escolhas de escopo, a partir de ["Fora de escopo"](./docs/requirements.md#fora-de-escopo) no catálogo, com o próximo passo de cada uma:

- **Autenticação e autorização de usuários.** Não há login. **Risco aceito:** qualquer pessoa com acesso à tela pode registrar a autorização de qualquer `beneficiarioId`. Próximo passo: Spring Security com OAuth2/OIDC ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)).
- **Separação de personas.** O beneficiário daria a autorização, e o operador da nova credora conduziria a portabilidade, em telas separadas ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)).
- **Identificação real do beneficiário**, em vez do identificador digitado ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)).
- **Expiração e revogação da autorização** ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)).
- **Acompanhamento no backend.** Um job consultando a Central de Convênios para as operações pendentes limitaria o atraso dos eventos finais; depois, SSE/WebSocket para empurrar as mudanças à tela. O job é pré-requisito do push ([ADR-0008](./docs/adr/0008-status-tracking-by-client-polling.md)).
- **Verificação da assinatura HMAC pela Central de Convênios**, que exigiria alterar o simulador ([ADR-0009](./docs/adr/0009-hmac-signed-central-requests.md)).
- **Recuperação de protocolos órfãos** após timeout no envio, que depende de idempotência do lado da Central de Convênios ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)).

## 11. Como a IA foi usada

Usei o Claude Code (agente de linha de comando) do planejamento à entrega, com skills que conduzem cada etapa. As decisões de escopo e de arquitetura e a aprovação de cada PR foram minhas; o agente redigiu os documentos, escreveu o código e os testes e fez as revisões.

1. **Planejamento.** Com a skill `grilling`, o agente me questionou sobre o briefing até cada ponto aberto virar uma decisão. A skill `domain-modeling` registrou essas decisões nos [ADRs](./docs/adr/) e no [GLOSSARY.md](./GLOSSARY.md), e o [catálogo de requisitos](./docs/requirements.md) liga cada requisito ao ADR que o atende (PR #1, só documentação).
2. **PRDs e tickets.** Cada fatia vertical virou um PRD (#2, #3, #4) e tickets como sub-issues no GitHub, com as dependências entre eles registradas como "blocked by" nativo (por exemplo, #15 depende de #11 e #12).
3. **Implementação.** Para cada PRD, uma branch de integração; para cada ticket, um subagente numa worktree isolada, trabalhando com TDD (skill `tdd`) nas três camadas de teste do [ADR-0010](./docs/adr/0010-test-strategy.md). Para APIs de bibliotecas recentes (Spring Boot 4, TanStack), o agente consultou a documentação atual pelo Context7.
4. **Revisão.** As PRs de código passaram por revisão antes do merge. Na PR #10 a revisão gerou correções (registro serializado por beneficiário, validação do `beneficiarioId`, erros inesperados da Central como `502`) e um refactor guiado pelas skills `clean-code` e `solid`. Na PR #17, uma revisão de esforço alto feita pelo agente apontou 10 problemas: seis foram corrigidos na própria PR.
5. **Onde a IA não teve a palavra final.** Parte dos apontamentos da PR #17 foi recusada porque contrariava decisões já registradas: a chamada à Central dentro da transação ([ADR-0005](./docs/adr/0005-idempotent-portability-requests.md)), a falta de autorização no `GET` da operação ([ADR-0004](./docs/adr/0004-single-flow-without-authentication.md)) e o 4xx da Central no envio sem `FALHA_ENVIO` ([ADR-0007](./docs/adr/0007-operation-states-and-event-trail.md)). Os ADRs serviram de critério para aceitar ou recusar o que o agente propunha.

As skills versionadas no repositório estão em [`.agents/skills/`](./.agents/skills/), com a origem em [`skills-lock.json`](./skills-lock.json); as demais (`grilling`, `domain-modeling`, revisão de código) vêm da minha instalação local do Claude Code.
