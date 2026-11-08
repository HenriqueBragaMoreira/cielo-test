import { cn } from "cn"
import type { ComponentProps, ReactNode } from "react"

const tituloSecao =
  "text-xs font-bold tracking-[0.08em] text-muted-foreground uppercase"

/** Duas colunas: a agenda à esquerda e o resumo fixo ao rolar à direita; em telas estreitas o resumo empilha abaixo. */
export function Workspace({
  cabecalho,
  agenda,
  resumo,
}: {
  cabecalho: ReactNode
  agenda: ReactNode
  resumo?: ReactNode
}) {
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_25rem]">
      <div className="flex min-w-0 flex-col gap-5">
        {cabecalho}
        {agenda}
      </div>
      {resumo}
    </div>
  )
}

export function CartaoAgenda({
  palavrasGuia,
  className,
  children,
  ...props
}: ComponentProps<"section"> & {
  /** O primeiro e o último mês da régua da agenda. */
  palavrasGuia?: string
}) {
  return (
    <section
      aria-labelledby="agenda-titulo"
      className={cn(
        "rounded-lg bg-card px-5 py-6 ring-1 ring-border sm:px-7",
        className
      )}
      {...props}
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="agenda-titulo" className={tituloSecao}>
          Agenda de descontos
        </h2>
        {palavrasGuia && <p className={tituloSecao}>{palavrasGuia}</p>}
      </div>
      {children}
    </section>
  )
}

export function PainelResumo({
  className,
  children,
  ...props
}: ComponentProps<"aside">) {
  return (
    <aside
      aria-labelledby="resumo-titulo"
      className={cn(
        "flex flex-col gap-5 rounded-lg bg-card p-6 ring-1 ring-border sm:p-7 lg:sticky lg:top-6",
        className
      )}
      {...props}
    >
      <h2 id="resumo-titulo" className={tituloSecao}>
        Resumo da operação
      </h2>
      {children}
    </aside>
  )
}
