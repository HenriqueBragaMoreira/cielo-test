import { CircleCheckIcon } from "lucide-react"

import { formatarDataHora } from "@/lib/formato"

import type { RegistroAutorizacao } from "./api"

const rotulo = "text-xs font-bold text-muted-foreground"

/** Os dados da autorização como campos rotulados, abaixo do título do beneficiário. */
export function ConfirmacaoAutorizacao({
  registro,
}: {
  registro: RegistroAutorizacao
}) {
  const { autorizacao, nova } = registro
  return (
    <dl
      role="status"
      className="flex flex-wrap gap-x-8 gap-y-3 text-sm sm:divide-x sm:divide-border [&>div]:flex [&>div]:flex-col [&>div]:gap-1 sm:[&>div:not(:last-child)]:pr-8"
    >
      <div>
        <dt className={rotulo}>Autorização</dt>
        <dd className="flex items-center gap-1.5 font-semibold text-success">
          <CircleCheckIcon aria-hidden className="size-4 shrink-0" />
          {nova ? "Registrada em" : "Já existia, registrada em"}{" "}
          {formatarDataHora(autorizacao.autorizadaEm)}
        </dd>
      </div>
      <div className="min-w-0">
        <dt className={rotulo}>Identificador na Central</dt>
        <dd className="break-all">{autorizacao.autorizacaoId}</dd>
      </div>
    </dl>
  )
}
