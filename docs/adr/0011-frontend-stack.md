# Stack do frontend

- **TanStack Router**: rotas e parâmetros tipados, com uma rota por passo do fluxo ([0004](./0004-single-flow-without-authentication.md)), e coerência com o TanStack Query.
- **TanStack Query**: `refetchInterval` para o polling ([0008](./0008-status-tracking-by-client-polling.md)), `isPending` para desabilitar o envio ([0005](./0005-idempotent-portability-requests.md)) e cancelamento com `AbortSignal`.
- **React Hook Form + Zod**: validação de formato da conta de recebimento de destino (`banco` com 3 dígitos, `agencia` com 4 dígitos, `conta` numérica e `digito`).
- **Tailwind CSS + shadcn/ui com Base UI**: componentes copiados para o projeto sobre primitivas Base UI. O frontend foi criado com `pnpm dlx shadcn@latest init --preset beqGOBCi --template vite`.
- **pnpm** como gerenciador de pacotes.

## Opções consideradas

- **React Router**: descartado em favor da tipagem de ponta a ponta do TanStack Router.
- **shadcn/ui sobre Radix**: descartado; preferimos as primitivas Base UI.
- **npm**: dispensaria instalação extra, mas preferimos o pnpm.

## Consequências

- Para rodar o frontend localmente é preciso `corepack enable`. A imagem Docker já cuida disso.
