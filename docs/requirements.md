# Catálogo de requisitos

Requisitos do [briefing do desafio](../desafio-tecnico-fullstack-react-java-candidato-junior-pleno.md), com a interpretação adotada e os ADRs que os atendem. Vocabulário conforme o [glossário](../GLOSSARY.md); decisões em [`docs/adr/`](./adr/).

## Requisitos funcionais

| ID | Requisito (briefing) | Interpretação adotada | Decidido em |
|---|---|---|---|
| RF-01 | Registrar a autorização (opt-in) de um beneficiário | Primeiro passo de um fluxo único: informar o `beneficiarioId` e marcar o aceite ("O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas"). A autorização é registrada na Central de Convênios e guardada localmente com `autorizacaoId` e data/hora. Registrar de novo devolve a autorização existente, sem nova chamada à Central. | [0004](./adr/0004-single-flow-without-authentication.md) |
| RF-02 | Consultar a agenda de um beneficiário **autorizado**, exibindo valor, data de desconto e status | A agenda só é servida para beneficiários com autorização registrada no nosso banco (`403` caso contrário), porque a Central não aplica essa regra. Cada parcela vem com `elegivel` e `motivoInelegibilidade`. | [0004](./adr/0004-single-flow-without-authentication.md), [0006](./adr/0006-eligibility-validated-against-central.md) |
| RF-03 | Selecionar parcelas e iniciar uma operação de portabilidade informando a conta de recebimento de destino | Conta de recebimento de destino = `{ banco, agencia, conta, digito }`, com validação de formato (banco com 3 dígitos COMPE, agência com 4 dígitos, conta numérica) e sem verificar se a conta existe. O backend revalida as parcelas contra a agenda da Central e contra operações ativas. | [0005](./adr/0005-idempotent-portability-requests.md), [0006](./adr/0006-eligibility-validated-against-central.md) |
| RF-04 | Acompanhar o status (pendente, confirmada, rejeitada), lidando com a espera | A tela consulta o backend no intervalo do `Retry-After` (2 s) até um estado final, com botão "Atualizar" de fallback e linha do tempo de eventos. | [0007](./adr/0007-operation-states-and-event-trail.md), [0008](./adr/0008-status-tracking-by-client-polling.md) |
| RF-05 | Tratar e exibir rejeição e falha de comunicação com a Central | A rejeição aparece com o motivo dado pela Central. Falhas de envio viram `FALHA_ENVIO`, que pode ser reenviada. Falhas no acompanhamento mantêm `PENDENTE` com aviso. A falha de comunicação pode ser demonstrada pela UI desligando o simulador, já que o `BEN_ERRO` tem agenda vazia. | [0006](./adr/0006-eligibility-validated-against-central.md), [0007](./adr/0007-operation-states-and-event-trail.md) |

## Requisitos não funcionais

| ID | Requisito (briefing) | Interpretação adotada | Decidido em |
|---|---|---|---|
| RNF-01 | Evitar duplicidade: reenvio (duplo clique, retry após falha de conexão) não cria duas operações para a mesma intenção | Duas camadas: `Idempotency-Key` gerada pelo frontend por intenção e garantida no banco, e a regra "uma parcela só pode estar em uma operação ativa", também garantida no banco. No frontend, o botão fica desabilitado durante o envio. | [0005](./adr/0005-idempotent-portability-requests.md) |
| RNF-02 | Tratamento explícito de erros de integração | Timeouts explícitos, nenhum retry automático em chamadas não idempotentes, cada resposta da Central (503, 404, timeout, conexão recusada) mapeada para um estado e uma mensagem, e erros entregues ao frontend em `application/problem+json`. | [0007](./adr/0007-operation-states-and-event-trail.md) |
| RNF-03 | Código organizado e de fácil manutenção | Backend organizado por funcionalidade, com um adaptador único para a Central e vocabulário de domínio igual ao da Central. | [0001](./adr/0001-portuguese-domain-language.md), [0002](./adr/0002-backend-as-sole-central-integrator.md) |
| RNF-04 | Testes automatizados cobrindo pelo menos os fluxos principais | Testes unitários e de integração no backend, testes de componente no frontend, E2E com Playwright e CI no GitHub Actions. | [0010](./adr/0010-test-strategy.md) |
| RNF-05 | Contar no README como a IA foi usada | Seção própria no README. | — |

## Restrições técnicas

| ID | Restrição (briefing) | Interpretação adotada | Decidido em |
|---|---|---|---|
| RT-01 | Backend em Java | Spring Boot 4.1 com Java 25. | [0002](./adr/0002-backend-as-sole-central-integrator.md) |
| RT-02 | Frontend em React (TypeScript bem-vindo) | React + TypeScript com Vite. | [0002](./adr/0002-backend-as-sole-central-integrator.md), [0011](./adr/0011-frontend-stack.md) |
| RT-03 | Banco de dados livre | PostgreSQL com Flyway. | [0003](./adr/0003-postgresql-and-docker-compose.md) |
| RT-04 | Executável com instruções claras no README | `docker compose up` sobe tudo, e o modo de desenvolvimento fica documentado. | [0003](./adr/0003-postgresql-and-docker-compose.md) |
| RT-05 | Bibliotecas externas permitidas | Bibliotecas listadas e justificadas no README. | [0011](./adr/0011-frontend-stack.md) |

## Diferenciais

| ID | Diferencial (briefing) | Interpretação adotada | Decidido em |
|---|---|---|---|
| D-01 | Verificação de integridade da mensagem enviada à Central | Toda requisição à Central é assinada com HMAC-SHA256. A verificação cabe à Central, e o simulador a ignora. | [0009](./adr/0009-hmac-signed-central-requests.md) |
| D-02 | Histórico/trilha de eventos de cada operação | Tabela de eventos só de inserção, gravada na mesma transação de cada mudança de estado e exibida como linha do tempo. | [0007](./adr/0007-operation-states-and-event-trail.md) |
| D-03 | Testes cobrindo cenários de erro | Testes de integração com WireMock cobrindo 503, 404, timeout, envio concorrente e conflitos. | [0010](./adr/0010-test-strategy.md) |
| D-04 | Melhorias de UX | Botão desabilitado durante o envio, parcelas inelegíveis desabilitadas com o motivo, indicação de espera, linha do tempo da operação e mensagens de erro com a ação possível (ex.: "Tentar novamente"). | [0005](./adr/0005-idempotent-portability-requests.md), [0007](./adr/0007-operation-states-and-event-trail.md), [0008](./adr/0008-status-tracking-by-client-polling.md) |

## Entrega

| ID | Item (briefing) | Interpretação adotada |
|---|---|---|
| E-01 | Repositório público no GitHub | `HenriqueBragaMoreira/cielo-test`, que hoje é privado e se torna público na entrega. |
| E-02 | README: instruções de execução (backend, frontend e simulador) | Compose completo mais o modo de desenvolvimento. |
| E-03 | README: decisões e porquês | Resumo com links para os ADRs. |
| E-04 | README: bibliotecas externas e justificativas | Lista com uma linha de justificativa cada. |
| E-05 | README: como a IA foi usada | Ver RNF-05. |
| E-06 | README: o que ficou de fora e próximos passos | Ver "Fora de escopo" abaixo. |

## Contrato da API (referência)

Todas as rotas ficam sob `/api`, e os erros vêm em `application/problem+json`.

| Método e rota | Comportamento |
|---|---|
| `POST /api/autorizacoes` `{ beneficiarioId }` | `201` na primeira vez, `200` se já existir → `{ beneficiarioId, autorizacaoId, autorizadaEm }` |
| `GET /api/beneficiarios/{id}/agenda` | Parcelas com `elegivel` e `motivoInelegibilidade`. `403` sem autorização. |
| `POST /api/operacoes-portabilidade` + `Idempotency-Key` `{ beneficiarioId, parcelaIds, contaDestino }` | `202` com a operação. Mesma chave e mesmo corpo → a mesma operação. Mesma chave com corpo diferente → `422`. Parcela já em operação ativa → `409`. Falha de envio → `503` com o `id` da operação em `FALHA_ENVIO`. |
| `GET /api/operacoes-portabilidade/{id}` | Status, protocolo, motivo de rejeição, parcelas, conta e `eventos[]`. Header `Retry-After: 2` enquanto `PENDENTE`. |

O `{id}` é um UUID gerado por nós, e o protocolo da Central é um campo da operação. A documentação OpenAPI fica disponível em `/swagger-ui.html`.

## Fora de escopo

- Autenticação e autorização de usuários (Spring Security + OAuth2/OIDC).
- Separação de personas: o beneficiário dá a autorização e o operador da nova credora conduz a portabilidade.
- Identificação real do beneficiário.
- Expiração e revogação da autorização.
- Acompanhamento de status por job no backend, e depois push via SSE/WebSocket.
- Verificação da assinatura HMAC pela Central.
- Recuperação de protocolos órfãos na Central após timeout no envio.
