# Requisições à Central assinadas com HMAC

Toda requisição à Central é assinada com HMAC-SHA256, usando um segredo compartilhado vindo de uma variável de ambiente (`CENTRAL_HMAC_SECRET`). A assinatura cobre o texto canônico `timestamp + "\n" + método + "\n" + caminho + "\n" + corpo`, com corpo vazio em `GET`, e vai nos headers `X-Assinatura` e `X-Timestamp`. Incluir o timestamp na assinatura permite que a Central recuse mensagens antigas (replay). A implementação usa só `javax.crypto.Mac`, sem biblioteca adicional, e fica no adaptador `central/` ([0002](./0002-backend-as-sole-central-integrator.md)).

## Consequências

- **A verificação é responsabilidade da Central**, e o simulador ignora os headers. Não alteramos o simulador, porque o avaliador roda a própria cópia. Um teste unitário verifica a assinatura usando o mesmo segredo.
- O `compose.yaml` traz um segredo padrão de desenvolvimento. O README deixa claro que um segredo real nunca seria versionado.
- Verificar a assinatura do lado da Central fica fora de escopo.
