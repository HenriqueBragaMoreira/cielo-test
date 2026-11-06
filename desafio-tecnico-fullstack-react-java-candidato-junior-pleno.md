# Desafio Técnico — Desenvolvedor(a) Fullstack Júnior/Pleno (React + Java)

## Plataforma de Portabilidade de Convênios de Desconto em Folha

### Antes de começar

Este desafio não espera uma solução perfeita nem 100% das funcionalidades implementadas.
Preferimos uma entrega **menor, mais clara e bem explicada** do que uma tentativa de cobrir tudo às pressas. Se precisar fazer escolhas de escopo, documente a escolha no README — isso conta a seu favor, não contra.
Os itens na seção **"Diferenciais"**, no fim deste documento, são bônus: não são obrigatórios e não eliminam ninguém por estarem ausentes.

### Contexto

Você foi contratado(a) para desenvolver uma plataforma que permite a uma empresa atuar como nova credora de convênios de desconto em folha, assumindo o direito de receber parcelas que hoje pertencem a outro credor — processo conhecido como **portabilidade de convênio**.
Para isso, a plataforma precisa se conectar a uma **Central de Convênios** (sistema externo que concentra as autorizações dos beneficiários e a agenda de descontos futuros), capturar a autorização do beneficiário, consultar essa agenda, registrar a operação de portabilidade e acompanhar sua confirmação.
Para este desafio, **já fornecemos um simulador pronto da Central de Convênios** (seção "Simulador fornecido", abaixo) — você não precisa construí-lo, só consumir os endpoints dele.
Após a entrega, haverá uma sessão de code review e apresentação para explicar decisões técnicas, trade-offs e possíveis evoluções.

### Objetivo

Construir uma solução **fullstack** (backend em **Java** + frontend em **React**) que simule o fluxo principal de portabilidade de convênio de desconto em folha, integrando com o simulador da Central de Convênios fornecido.

### Funcionalidades Esperadas

- Captura simples da autorização (opt-in) do beneficiário
- Consulta e visualização da agenda de descontos futuros de um beneficiário
- Seleção das parcelas elegíveis para portabilidade
- Registro da operação de portabilidade, com acompanhamento até a confirmação ou rejeição
- Exibição clara do resultado e de eventuais erros ao usuário

### Requisitos Funcionais

Implemente ao menos os seguintes fluxos:

1. Registrar a autorização (opt-in) de um beneficiário
2. Consultar a agenda de descontos futuros de um beneficiário autorizado, exibindo parcelas com valor, data de desconto e status
3. Selecionar uma ou mais parcelas e iniciar uma operação de portabilidade, informando a conta de recebimento de destino
4. Acompanhar o status da operação (pendente, confirmada, rejeitada) — a confirmação não é imediata, então a tela precisa lidar com esse tempo de espera de alguma forma (loading, atualização automática, botão de atualizar — a escolha é sua)
5. Tratar e exibir ao usuário cenários de rejeição ou de falha na comunicação com a Central (o simulador fornecido tem um cenário pronto para testar isso — veja "Simulador fornecido")

### Requisitos Não-Funcionais

- **Evitar duplicidade:** se o usuário reenviar a mesma solicitação (ex.: clicar duas vezes no botão, ou a conexão falhar e o app tentar de novo), isso não pode criar duas operações diferentes para a mesma intenção. Não existe uma única forma "certa" de resolver isso — queremos ver como você pensa o problema.
- Tratamento explícito de erros de integração (ex.: quando a Central está indisponível)
- Código organizado e de fácil manutenção
- Testes automatizados cobrindo pelo menos os fluxos principais
- Fazer uso de ferramenta(s) de IA como apoio à implementação é esperado — conte, no README, como você usou (não existe resposta certa; queremos entender seu processo, não cobrar um formato específico)

### Restrições Técnicas

- Backend: **Java** (framework livre — Spring Boot é o mais comum, mas não obrigatório)
- Frontend: **React** (TypeScript é bem-vindo, mas não obrigatório)
- Banco de dados: livre
- O projeto deve ser executável com instruções claras no README
- É permitido (e recomendado) uso de bibliotecas externas

### Simulador Fornecido — Central de Convênios

Salve o código abaixo como `mock-central.js` e rode com `node mock-central.js` (não precisa instalar nada — usa só módulos nativos do Node). Ele sobe em `http://localhost:4000`.

```js
// mock-central.js — simulador da Central de Convênios (fornecido para o desafio)
// Rodar com: node mock-central.js

const http = require('http');
const { randomUUID } = require('crypto');

const AGENDAS = {
  BEN001: [
    { parcelaId: 'P001', convenioId: 'CONV01', valor: 150.00, dataDesconto: '2026-01-10', status: 'ABERTA' },
    { parcelaId: 'P002', convenioId: 'CONV01', valor: 150.00, dataDesconto: '2026-02-10', status: 'ABERTA' },
  ],
  BEN002: [
    { parcelaId: 'P010', convenioId: 'CONV02', valor: 300.00, dataDesconto: '2026-01-15', status: 'ABERTA' },
  ],
};

const operacoes = new Map();

function send(res, status, body, extraHeaders = {}) {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', ...extraHeaders });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch { resolve({}); }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const parts = url.pathname.split('/').filter(Boolean);

  if (req.method === 'POST' && parts[0] === 'autorizacoes') {
    await readBody(req);
    return send(res, 201, { autorizacaoId: randomUUID() });
  }

  if (req.method === 'GET' && parts[0] === 'beneficiarios' && parts[2] === 'agenda') {
    return send(res, 200, AGENDAS[parts[1]] || []);
  }

  if (req.method === 'POST' && parts[0] === 'operacoes-portabilidade') {
    const body = await readBody(req);

    if (body.beneficiarioId === 'BEN_ERRO') {
      return send(res, 503, { erro: 'Central indisponível no momento (simulado). Tente novamente.' });
    }

    const protocolo = randomUUID();
    const vaiRejeitar = body.beneficiarioId === 'BEN002';
    operacoes.set(protocolo, { resolveEm: Date.now() + 5000, vaiRejeitar });
    return send(res, 202, { protocolo });
  }

  if (req.method === 'GET' && parts[0] === 'operacoes-portabilidade' && parts[1]) {
    const op = operacoes.get(parts[1]);
    if (!op) return send(res, 404, { erro: 'Protocolo não encontrado' });

    if (Date.now() < op.resolveEm) {
      return send(res, 204, undefined, { 'Retry-After': '2' });
    }
    return send(res, 200, {
      status: op.vaiRejeitar ? 'REJEITADA' : 'CONFIRMADA',
      motivoRejeicao: op.vaiRejeitar ? 'Beneficiário atingiu o limite de margem consignável' : null,
    });
  }

  send(res, 404, { erro: 'Rota não encontrada' });
});

server.listen(4000, () => console.log('Central de Convênios (simulada) em http://localhost:4000'));
```

**Cenários de teste já prontos no simulador** (use esses IDs para testar sem depender de sorte):

| `beneficiarioId` | O que acontece |
|---|---|
| `BEN001` | Agenda com 2 parcelas; a operação de portabilidade é **confirmada** após ~5 segundos |
| `BEN002` | Agenda com 1 parcela; a operação de portabilidade é **rejeitada** após ~5 segundos |
| `BEN_ERRO` | A Central responde imediatamente que está **indisponível** (erro 503) — use para testar o tratamento de erro |

### Diferenciais (opcional — não é obrigatório, mas conta pontos extras)

- Alguma forma de verificação de integridade da mensagem enviada à Central (ex.: um HMAC simples)
- Histórico/trilha de eventos de cada operação (quando foi solicitada, quando mudou de status)
- Testes cobrindo também os cenários de erro (ex.: `BEN_ERRO`)
- Qualquer melhoria de UX que você julgar relevante

### Entrega

- Crie um repositório público no GitHub com a solução
- Envie o link no prazo combinado
- Inclua no README:
  - Instruções de execução (backend, frontend e o simulador)
  - Decisões que você tomou e por quê
  - Bibliotecas externas utilizadas e justificativas
  - Como você usou IA no processo
  - O que ficou de fora por falta de tempo, e o que faria a seguir
