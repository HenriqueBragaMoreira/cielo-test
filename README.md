# Portabilidade de Convênios

Solução fullstack (Java + React) para o [desafio técnico](./desafio-tecnico-fullstack-react-java-candidato-junior-pleno.md) de portabilidade de convênios de desconto em folha, integrada ao simulador da Central de Convênios fornecido.

## 1. Visão geral

**Problema.** Uma nova credora quer assumir o direito de receber parcelas de convênios de desconto em folha que hoje pertencem a outro credor. Para isso precisa da autorização do beneficiário, consultar a agenda de parcelas na Central de Convênios, pedir a portabilidade das parcelas escolhidas e acompanhar a decisão, que não é imediata. Reenvios (duplo clique, retry após falha de conexão) não podem criar duas operações para a mesma intenção.

**Solução.** Um fluxo único em três telas:

1. **Autorização**: o beneficiário informa seu identificador e autoriza a consulta e a portabilidade; a autorização é registrada na Central de Convênios.
2. **Agenda**: as parcelas vêm da Central de Convênios, com a elegibilidade de cada uma. O usuário seleciona as parcelas e informa a conta de recebimento de destino.
3. **Operação de portabilidade**: a tela acompanha a operação até a confirmação ou a rejeição, com a linha do tempo de eventos e os erros de integração explicados.

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

## 7. Bibliotecas e justificativas

## 8. Diferenciais implementados

## 9. Limitações conhecidas

## 10. Fora de escopo e próximos passos

## 11. Como a IA foi usada
