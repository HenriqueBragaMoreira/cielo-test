# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Operador de backoffice da nova credora. Ele conduz a portabilidade em nome do beneficiário: registra o opt-in, consulta a agenda, escolhe as parcelas, informa a conta de destino e acompanha a decisão da Central de Convênios. É uma ferramenta interna e técnica, usada repetidas vezes, em que eficiência e clareza de estado importam mais do que explicação.

Público secundário, sem ser persona da UI: o recrutador ou avaliador da Cielo que revisa o teste técnico. Ele usa a interface como um operador usaria; os cenários de teste (BEN001, BEN002, BEN_ERRO) ficam só no README e não aparecem na UI.

## Product Purpose

Permitir que a empresa atue como nova credora de convênios de desconto em folha, assumindo parcelas que hoje pertencem a outro credor, por meio de integração com a Central de Convênios. O sucesso é o operador concluir a portabilidade sem enviar operações duplicadas e entender a todo momento em que estado a operação está e o que pode fazer em seguida.

## Positioning

Fluxo único de ponta a ponta (autorização → agenda → operação → decisão) com garantias visíveis: idempotência por intenção, elegibilidade validada contra a Central, trilha de eventos da operação e assinatura HMAC das requisições.

## Operating Context

- O backend é o único integrador da Central (ADR-0002); a confirmação é assíncrona (~5 s) e acompanhada por polling seguindo o `Retry-After` (ADR-0008).
- Sem autenticação: um fluxo único (ADR-0004).
- A Central pode ficar indisponível (503), rejeitar (com motivo) ou deixar de reconhecer o protocolo.

## Capabilities and Constraints

- Estados da operação: PENDENTE, CONFIRMADA, REJEITADA, FALHA_ENVIO; eventos SOLICITADA, ENVIADA, FALHA_ENVIO, CONFIRMADA, REJEITADA (ADR-0007).
- Parcela: id, convênio, valor, data de desconto, status, elegível e motivo de inelegibilidade.
- Conta de destino: banco (3 dígitos), agência (4), conta (só números), dígito (1).
- Reenvio com a mesma `Idempotency-Key` em 503 ou sem resposta; o 422 renova a intenção; 409 e 422 recarregam a agenda (ADR-0005).
- Vocabulário de domínio em português (ADR-0001, GLOSSARY.md).
- O redesign pode reorganizar rotas e fluxo, desde que preserve as regras acima, o contrato da API e a cobertura de testes (componente + E2E Playwright), que acompanham a mudança.
- Decidido para o redesign: workspace com o resumo da operação fixo ao lado da agenda (vira o acompanhamento depois do envio) e um histórico de operações, que exige uma nova rota de listagem no backend (`GET /api/operacoes-portabilidade`), com teste.
- Só tema claro. O modo escuro sai.

## Brand Commitments

- A UI se apresenta como um produto interno da Cielo: logo Cielo no header e identidade visual Cielo (azul-marinho #1E4B8C aprox. com o azul-ciano do "o", wordmark branco). Logo de referência fornecida pelo usuário em `C:\Users\kanat\Downloads\cielo_logo.jpg`.
- Logo recriada em SVG a partir da referência (versão branca sobre azul e versão azul sobre branco).
- O que o avaliador não pode ver: estado ambíguo da operação, cara de landing page (hero, gradientes, marketing), cara de template shadcn genérico e fantasia de marca (azul Cielo em tudo, logo gigante). A marca deve aparecer com precisão, não com volume.
- Obrigatório um aviso discreto de que é um projeto de avaliação técnica, sem vínculo oficial com a Cielo (o repositório é público).

## Evidence on Hand

- Brief: `desafio-tecnico-fullstack-react-java-candidato-junior-pleno.md`; requisitos: `docs/requirements.md`; decisões: `docs/adr/`.
- Dados reais só os do simulador. Não inventar clientes, métricas, volumes ou depoimentos.

## Product Principles

1. O estado da operação nunca é ambíguo: o operador sempre sabe o que aconteceu e o que pode fazer agora.
2. Errar não duplica: cada ação reenviável comunica que é segura de repetir.
3. Densidade de backoffice: informação escaneável e números alinhados, sem explicação didática.
4. Toda falha da Central tem uma saída (tentar novamente, voltar, ver a operação).

## Accessibility & Inclusion

Manter o que já existe: rótulos acessíveis, `role="status"`/`role="alert"` nas mudanças de estado, `aria-busy`, foco por teclado. Alvo WCAG 2.2 AA.
