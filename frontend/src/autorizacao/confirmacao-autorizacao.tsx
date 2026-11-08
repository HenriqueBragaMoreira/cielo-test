import { CircleCheckIcon } from "lucide-react"

import { formatarDataHora } from "@/lib/formato"

import type { RegistroAutorizacao } from "./api"

export function ConfirmacaoAutorizacao({
  registro,
}: {
  registro: RegistroAutorizacao
}) {
  const { autorizacao, nova } = registro
  return (
    <div
      role="status"
      className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm"
    >
      <p className="flex items-center gap-1.5 font-semibold text-success">
        <CircleCheckIcon aria-hidden className="size-4 shrink-0" />
        <span>
          {nova
            ? "Autorização registrada em"
            : "Autorização já existia, registrada em"}{" "}
          {formatarDataHora(autorizacao.autorizadaEm)}.
        </span>
      </p>
      <p className="text-xs text-muted-foreground">
        Identificador na Central: {autorizacao.autorizacaoId}
      </p>
    </div>
  )
}
