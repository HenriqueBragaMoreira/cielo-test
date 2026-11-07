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
      className="flex flex-col gap-1 rounded-lg border p-3 text-sm"
    >
      <p>
        {nova
          ? `Autorização do beneficiário ${autorizacao.beneficiarioId} registrada em`
          : `O beneficiário ${autorizacao.beneficiarioId} já tinha uma autorização registrada em`}{" "}
        {formatarDataHora(autorizacao.autorizadaEm)}.
      </p>
      <p className="text-muted-foreground">
        Identificador na Central: {autorizacao.autorizacaoId}
      </p>
    </div>
  )
}
