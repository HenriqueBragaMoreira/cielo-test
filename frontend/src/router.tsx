import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  type RouterHistory,
} from "@tanstack/react-router"

import { AutorizacaoPage } from "@/autorizacao/autorizacao-page"

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

const routeTree = rootRoute.addChildren([autorizacaoRoute])

export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
