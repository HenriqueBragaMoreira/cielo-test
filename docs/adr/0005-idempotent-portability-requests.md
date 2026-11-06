# Requisições de portabilidade idempotentes

A Central não é idempotente: cada `POST /operacoes-portabilidade` gera um protocolo novo. Por isso toda a garantia de "uma operação por intenção" (RNF-01) é nossa, e ela tem duas camadas, ambas garantidas no banco.

1. **Chave de idempotência.** O frontend gera um UUID quando o formulário abre e o envia no header `Idempotency-Key`. Ele reutiliza a mesma chave em qualquer reenvio da mesma intenção e só gera uma chave nova quando o formulário muda ou depois de um resultado final. O backend guarda a chave com `UNIQUE`, junto com um hash do corpo:
   - mesma chave e mesmo corpo devolvem a mesma operação, sem nova chamada à Central;
   - se essa operação estiver em `FALHA_ENVIO`, o reenvio tenta enviar *a mesma* operação de novo ([0007](./0007-operation-states-and-event-trail.md));
   - mesma chave com corpo diferente resulta em `422`.
2. **Uma parcela, uma operação ativa.** Uma parcela só pode estar em uma operação `PENDENTE` ou `CONFIRMADA`. Isso é garantido por uma constraint no banco, por exemplo um índice único parcial sobre as parcelas de operações ativas, para que nem duas abas com chaves diferentes consigam portar a mesma parcela; a segunda recebe `409`. Operações `REJEITADA` e `FALHA_ENVIO` liberam as parcelas.

## Ordem de gravação

O fluxo é: gravar a operação, chamar a Central e gravar o resultado. Isso acontece numa única transação, com a chamada à Central limitada pelo timeout de leitura de 5 s. A inserção reserva a chave e as parcelas. Uma requisição concorrente com a mesma chave fica bloqueada no índice único até o commit e então recebe a operação existente. O custo é manter uma conexão com o banco presa por até cerca de 7 s, o que é aceitável nesta escala.

## Frontend

- O botão de envio fica desabilitado enquanto a requisição está em andamento (`isPending`).
- `AbortSignal` **não** é mecanismo de deduplicação: ele cancela só o lado do navegador, e o servidor continua processando. Ele é usado apenas para cancelar o polling ao sair da tela.

## Consequências

- O recurso é identificado por um UUID nosso, não pelo protocolo da Central, porque operações em `FALHA_ENVIO` não têm protocolo.
- **Limitação:** um timeout no envio é ambíguo, porque a Central pode ter aceitado. A operação vai para `FALHA_ENVIO`, e um reenvio pode criar um segundo protocolo na Central, que fica órfão. Isso não tem solução sem idempotência do lado da Central.
