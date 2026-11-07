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
