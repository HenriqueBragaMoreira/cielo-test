# Acompanhamento de status por polling do cliente

A Central só oferece polling (`204` com `Retry-After: 2` enquanto decide) e não tem webhook. O frontend consulta `GET /api/operacoes-portabilidade/{id}` no intervalo do `Retry-After` (via `refetchInterval` do TanStack Query), para quando a operação chega a um estado final e cancela as requisições com `AbortSignal` ao sair da tela. Um botão "Atualizar" serve de fallback. No backend, cada `GET` de uma operação `PENDENTE` consulta a Central e registra a transição ([0007](./0007-operation-states-and-event-trail.md)).

## Opções consideradas

- **(B) Job agendado no backend** consultando a Central para todas as operações pendentes.
- **(C) SSE/WebSocket** empurrando as mudanças para o navegador.

## Por que a evolução é A → B → C

B e C resolvem problemas diferentes. O C muda como o *navegador* descobre uma mudança no *nosso backend*. O B muda como o *nosso backend* descobre uma mudança na *Central*. Como a Central só oferece polling, alguém no backend precisa consultá-la de qualquer forma, e o C não elimina isso: para empurrar algo para a tela, primeiro é preciso um processo que descubra a mudança sozinho, que é o B. **B é pré-requisito de C.** O C só compensa com muitas telas abertas ao mesmo tempo ou se a latência importar. Com um operador e uma espera de cerca de 5 s, um polling de 2 s custa praticamente nada.

## Consequências

- Uma operação sem ninguém olhando fica `PENDENTE` no nosso banco até alguém abrir a tela.
- A data/hora dos eventos finais na trilha é a da observação. O B limitaria esse atraso ao intervalo do job.
