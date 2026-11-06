# Elegibilidade validada contra a agenda da Central

Uma parcela é elegível quando está `ABERTA` na agenda da Central **e** não está numa operação nossa `PENDENTE` ou `CONFIRMADA` ([0005](./0005-idempotent-portability-requests.md)). A agenda devolvida ao frontend traz `elegivel` e `motivoInelegibilidade` por parcela. Ao criar uma operação, o backend relê a agenda da Central e recusa parcelas que não pertencem ao beneficiário ou que não são elegíveis. O frontend não é fonte de verdade.

O simulador nunca altera o status de uma parcela depois da portabilidade. Quem impede portar a mesma parcela de novo é a nossa trava, não a Central.

## Consequências

- O `BEN_ERRO` tem agenda vazia, então **pela UI não é possível chegar ao 503** no envio. Esse cenário fica coberto por teste de integração com WireMock ([0010](./0010-test-strategy.md)).
- A falha de comunicação pode ser demonstrada pela UI desligando o simulador (conexão recusada), e o README explica como.

## Opções consideradas

- **Aceitar uma operação sem parcelas só para o `BEN_ERRO`**: descartado, porque contamina a regra de negócio só para servir a um cenário de teste.
