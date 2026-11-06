# Fluxo único, sem autenticação

O briefing pede uma "captura **simples** da autorização". Por isso a aplicação é um fluxo linear único, sem login, sem identificação e sem separação de personas:
1. Informar o `beneficiarioId` e marcar o aceite, o que chama `POST /autorizacoes` na Central.
2. Ver a agenda e escolher as parcelas.
3. Informar a conta de recebimento de destino e enviar.
4. Acompanhar a operação até a confirmação ou a rejeição.

O briefing não pede autenticação e diz que escolhas de escopo documentadas contam a favor.

## Autorização exigida localmente

A Central não exige autorização para servir a agenda nem para aceitar operações. Quem garante a regra "beneficiário autorizado" (RF-02) somos nós:
- a agenda e a criação de operações respondem `403` quando não há autorização registrada no nosso banco;
- cada beneficiário tem uma única autorização, e registrar de novo devolve a existente (`200`) sem chamar a Central;
- guardamos `autorizacaoId` e a data/hora como evidência.

## Consequências

- **Risco aceito:** qualquer pessoa com acesso à tela pode registrar autorização para qualquer `beneficiarioId`. O README declara isso explicitamente.
- **Evolução:** Spring Security + OAuth2/OIDC; identificação real do beneficiário; separação de personas (o beneficiário dá a autorização, e o operador da nova credora conduz a portabilidade); expiração e revogação da autorização.

## Opções consideradas

- **Duas personas com telas separadas**: é o modelo mais fiel ao domínio, mas é escopo além do pedido.
- **Identificação simulada do beneficiário** (CPF + data de nascimento): exigiria inventar uma base de dados que a Central não tem.
