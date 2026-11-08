// Horário de Brasília: a plataforma e a Central operam no Brasil, e a exibição fica igual em qualquer máquina.
const fusoHorario = "America/Sao_Paulo"
const data = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeZone: fusoHorario,
})
const hora = new Intl.DateTimeFormat("pt-BR", {
  timeStyle: "short",
  timeZone: fusoHorario,
})

/** `2026-11-06T13:45:00Z` → `06/11/2026 às 10:45` */
export function formatarDataHora(isoInstant: string) {
  const instante = new Date(isoInstant)
  return `${data.format(instante)} às ${hora.format(instante)}`
}

const reais = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
})

/** `1500.5` → `R$ 1.500,50` */
export function formatarMoeda(valor: number) {
  return reais.format(valor)
}

/** `2026-01-10` → `10/01/2026`; sem `Date`, que leria a data como meia-noite UTC e voltaria um dia em Brasília. */
export function formatarData(isoData: string) {
  return isoData.split("-").reverse().join("/")
}

const partesDaData = new Intl.DateTimeFormat("en-US", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  timeZone: fusoHorario,
})

/** A data de hoje em Brasília, como `AAAA-MM-DD`; montada pelas partes, sem depender do formato de um locale. */
export function hojeEmBrasilia() {
  const partes = Object.fromEntries(
    partesDaData
      .formatToParts(new Date())
      .map(({ type, value }) => [type, value])
  )
  return `${partes.year}-${partes.month}-${partes.day}`
}
