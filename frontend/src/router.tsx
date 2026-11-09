import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
} from "@tanstack/react-router"

import { AgendaPage } from "@/agenda/agenda-page"
import type { RegistroAutorizacao } from "@/autorizacao/api"
import { AutorizacaoPage } from "@/autorizacao/autorizacao-page"
import { HistoricoPage } from "@/historico/historico-page"
import { OperacaoPage } from "@/portabilidade/operacao-page"
import { AppShell, type Passo } from "@/shell/app-shell"

const rootRoute = createRootRoute({ component: AppShell })

const autorizacaoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  staticData: { passo: 1 },
  component: AutorizacaoPage,
})

const agendaRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/beneficiarios/$beneficiarioId/agenda",
  staticData: { passo: 2 },
  remountDeps: ({ params }) => params.beneficiarioId,
  component: AgendaPage,
})

const operacaoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/operacoes-portabilidade/$operacaoId",
  staticData: { passo: 3 },
  component: OperacaoPage,
})

// sem `passo`: o histórico é uma entrada fora do fluxo, e o shell não mostra o stepper
const historicoRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/historico",
  component: HistoricoPage,
})

const routeTree = rootRoute.addChildren([
  autorizacaoRoute,
  agendaRoute,
  operacaoRoute,
  historicoRoute,
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
  interface StaticDataRouteOption {
    /** Passo do stepper que a rota representa; sem passo, o shell não mostra o stepper. */
    passo?: Passo
  }
}
