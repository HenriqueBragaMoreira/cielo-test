# Histórico de operações lido só do nosso banco

`GET /api/operacoes-portabilidade` lista as operações de portabilidade a partir do nosso banco, da mais recente para a mais antiga, **sem consultar a Central de Convênios**. Cada item traz identificador, beneficiário, status, quantidade de parcelas, valor total e as datas de criação e de última atualização. A consulta de uma operação pelo `id` continua sendo o único ponto que atualiza o status junto à Central ([0008](./0008-status-tracking-by-client-polling.md)).

## Opções consideradas

- **Atualizar as operações `PENDENTE` ao listar:** uma chamada à Central por operação pendente, com a latência e as falhas dela pesando na lista inteira. Também misturaria leitura com transição de estado num endpoint de listagem.
- **Job no backend (B do [0008](./0008-status-tracking-by-client-polling.md)):** resolveria a defasagem, mas é a evolução já registrada e não é necessária para a lista.

## Consequências

- Uma operação pode aparecer `PENDENTE` no histórico depois que a Central já decidiu. Ela se atualiza quando alguém a abre, e a tela do histórico deixa isso claro.
- A lista se limita às 50 operações mais recentes, sem paginação nem filtros. Isso basta para um operador numa demonstração; paginar fica como evolução.
