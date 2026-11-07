import { lancarErroApi } from "@/lib/api"

export type Parcela = {
  parcelaId: string
  convenioId: string
  valor: number
  /** `AAAA-MM-DD` */
  dataDesconto: string
  status: string
  elegivel: boolean
  motivoInelegibilidade: string | null
}

export async function consultarAgenda(beneficiarioId: string) {
  const resposta = await fetch(
    `/api/beneficiarios/${encodeURIComponent(beneficiarioId)}/agenda`
  )
  if (!resposta.ok) {
    await lancarErroApi(resposta)
  }
  const parcelas: Parcela[] = await resposta.json()
  return parcelas
}
