import { setupServer } from "msw/node"

/** Intercepta `/api` nos testes. Sem handlers padrão: cada teste declara as respostas com `server.use(...)`. */
export const server = setupServer()
