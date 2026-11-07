import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  type RouterHistory,
} from "@tanstack/react-router"

import { AgendaPage } from "@/agenda/agenda-page"
import type { RegistroAutorizacao } from "@/autorizacao/api"
import { AutorizacaoPage } from "@/autorizacao/autorizacao-page"
import { OperacaoPage } from "@/portabilidade/operacao-page"

const rootRoute = createRootRoute({
  component: () => (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col gap-6 p-6">
      <h1 className="font-heading text-xl font-medium">
        Portabilidade de Convênios
      </h1>
      <Outlet />
    </main>
  ),
})

const autorizacaoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: AutorizacaoPage,
})

const agendaRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/beneficiarios/$beneficiarioId/agenda",
  component: AgendaPage,
})

const operacaoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/operacoes-portabilidade/$operacaoId",
  component: OperacaoPage,
})

const routeTree = rootRoute.addChildren([
  autorizacaoRoute,
  agendaRoute,
  operacaoRoute,
])

export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
  interface HistoryState {
    /** A autorização recém-registrada, para a agenda mostrar a confirmação. */
    registroAutorizacao?: RegistroAutorizacao
  }
}
