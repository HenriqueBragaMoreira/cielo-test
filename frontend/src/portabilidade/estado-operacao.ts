import {
  CircleAlertIcon,
  CircleCheckIcon,
  CircleXIcon,
  ClockIcon,
  type LucideIcon,
} from "lucide-react"

import type { Operacao } from "./api"

/**
 * Palavra, ícone e cor de cada estado da operação, os mesmos em toda tela: o estado nunca fica só na cor. Pendente
 * é estado, em âmbar (o ciano fica para seleção, ação e passo atual).
 */
export const estadoDaOperacao: Record<
  Operacao["status"],
  {
    rotulo: string
    Icone: LucideIcon
    /** Texto e ícone. */
    tom: string
    /** Fundo da pílula de status. */
    veu: string
    /** Fundo e contorno do painel de situação. */
    superficie: string
  }
> = {
  PENDENTE: {
    rotulo: "Pendente",
    Icone: ClockIcon,
    tom: "text-warning",
    veu: "bg-warning/10",
    superficie:
      "bg-[color-mix(in_srgb,var(--warning)_7%,white)] ring-warning/30",
  },
  CONFIRMADA: {
    rotulo: "Confirmada",
    Icone: CircleCheckIcon,
    tom: "text-success",
    veu: "bg-success/10",
    superficie:
      "bg-[color-mix(in_srgb,var(--success)_7%,white)] ring-success/30",
  },
  REJEITADA: {
    rotulo: "Rejeitada",
    Icone: CircleXIcon,
    tom: "text-destructive",
    veu: "bg-destructive/10",
    superficie:
      "bg-[color-mix(in_srgb,var(--destructive)_5%,white)] ring-destructive/30",
  },
  FALHA_ENVIO: {
    rotulo: "Falha no envio",
    Icone: CircleAlertIcon,
    tom: "text-destructive",
    veu: "bg-destructive/10",
    superficie:
      "bg-[color-mix(in_srgb,var(--destructive)_5%,white)] ring-destructive/30",
  },
}
