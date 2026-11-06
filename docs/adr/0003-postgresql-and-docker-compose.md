# PostgreSQL com Flyway, executado via Docker Compose

O banco é PostgreSQL, com o schema versionado pelo Flyway. A deduplicação ([0005](./0005-idempotent-portability-requests.md)) depende de constraints `UNIQUE` e de um comportamento de concorrência confiável, e essas são garantias do Postgres que o H2 só imita. Um único `docker compose up` sobe tudo, e o app fica acessível em `http://localhost:8080`:
- `postgres`;
- `mock-central`: imagem `node` rodando o simulador sem alterações;
- `backend`;
- `frontend`: build estático servido por nginx, que faz proxy de `/api` para o backend. Como frontend e API ficam na mesma origem, não é preciso configurar CORS.

## Opções consideradas

- **H2 em arquivo**: zero instalação, mas com semântica de locks e constraints diferente da que se usaria em produção.
- **Somente memória**: perde as operações ao reiniciar, o que enfraquece o acompanhamento de status.

## Consequências

- O avaliador só precisa de Docker.
- No desenvolvimento, `docker compose up postgres mock-central` sobe as dependências, e o backend e o frontend rodam localmente.
- Os testes de integração usam Testcontainers com a mesma imagem do Postgres.
