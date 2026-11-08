import { Link, Outlet, useMatches } from "@tanstack/react-router"
import { cn } from "cn"
import { CheckIcon, HistoryIcon } from "lucide-react"

import { Logo } from "./logo"

export type Passo = 1 | 2 | 3

const PASSOS: { passo: Passo; rotulo: string }[] = [
  { passo: 1, rotulo: "Autorização" },
  { passo: 2, rotulo: "Parcelas e conta" },
  { passo: 3, rotulo: "Acompanhamento" },
]

/** Layout raiz: barra de topo, conteúdo da rota e aviso de avaliação. */
export function AppShell() {
  // o passo vem do `staticData` da rota mais profunda; rota sem passo (o histórico) não mostra stepper
  const passoAtual = useMatches({
    select: (matches) => matches.at(-1)?.staticData.passo,
  })

  return (
    <div className="flex min-h-svh flex-col">
      <header className="bg-brand-navy text-white">
        <div className="mx-auto flex min-h-16 max-w-screen-2xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-8 lg:py-0">
          <Logo />
          <p className="border-l border-white/30 pl-5 text-[0.9375rem] text-white/85">
            Portabilidade de convênios
          </p>
          <div className="flex w-full items-center gap-7 lg:ml-auto lg:w-auto">
            {passoAtual && <Stepper atual={passoAtual} />}
            {/* depois do stepper, um divisor separa a navegação fixa dos passos */}
            <nav
              aria-label="Navegação"
              className="ml-auto lg:ml-0 lg:[ol+&]:border-l lg:[ol+&]:border-white/25 lg:[ol+&]:pl-7"
            >
              <Link
                to="/historico"
                className="flex items-center gap-2 border-b-2 border-transparent py-1 text-sm text-white/75 transition-colors hover:text-white aria-[current=page]:border-brand-cyan aria-[current=page]:font-bold aria-[current=page]:text-white"
              >
                <HistoryIcon aria-hidden className="size-4" />
                Histórico
              </Link>
            </nav>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-screen-2xl flex-1 px-4 py-7 sm:px-8">
        <Outlet />
      </main>
      <footer className="mx-auto w-full max-w-screen-2xl px-4 pb-4 text-xs text-muted-foreground sm:px-8">
        Projeto de avaliação técnica, sem vínculo oficial com a Cielo.
      </footer>
    </div>
  )
}

function Stepper({ atual }: { atual: Passo }) {
  return (
    <ol
      aria-label="Etapas da portabilidade"
      className="flex items-center gap-5 text-sm sm:gap-7"
    >
      {PASSOS.map(({ passo, rotulo }) => {
        const concluido = passo < atual
        const corrente = passo === atual
        return (
          <li
            key={passo}
            aria-current={corrente ? "step" : undefined}
            className={cn(
              "flex items-center gap-2 border-b-2 py-1",
              corrente
                ? "border-brand-cyan font-bold text-white"
                : "border-transparent text-white/75"
            )}
          >
            <span
              className={cn(
                "grid size-5 place-content-center rounded-full text-xs font-bold",
                corrente && "bg-brand-cyan text-foreground",
                concluido && "bg-white/15",
                !corrente && !concluido && "ring-1 ring-white/50 ring-inset"
              )}
            >
              {concluido ? (
                <CheckIcon aria-hidden className="size-3" strokeWidth={3} />
              ) : (
                passo
              )}
              {concluido && <span className="sr-only">{passo}</span>}
            </span>
            <span className={cn(!corrente && "max-sm:sr-only")}>
              {rotulo}
              {concluido && <span className="sr-only"> (concluída)</span>}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
