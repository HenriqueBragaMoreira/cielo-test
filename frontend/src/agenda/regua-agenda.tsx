import { cn } from "cn"

import { formatarData, formatarMoeda } from "@/lib/formato"

import type { Parcela } from "./api"
import { niveisDosRotulos, rotuloNaTelaEstreita, type Eixo } from "./regua"

/** Selecionada acende em ciano; elegível fica na tinta, com o ponto vazado; inelegível fica em cinza. */
const marca = {
  selecionada: {
    haste: "bg-brand-cyan",
    ponto: "bg-brand-cyan ring-brand-cyan",
  },
  elegivel: { haste: "bg-foreground", ponto: "bg-card ring-foreground" },
  // cinza opaco: a haste e o ponto não deixam o eixo aparecer através deles
  inelegivel: {
    haste: "bg-[color-mix(in_srgb,var(--muted-foreground)_35%,white)]",
    ponto:
      "bg-[color-mix(in_srgb,var(--muted-foreground)_35%,white)] ring-[color-mix(in_srgb,var(--muted-foreground)_35%,white)]",
  },
}

// por nível de rótulo (até `totalDeNiveis`): cada nível acima ganha a altura de um rótulo a mais de haste
const alturaDaHaste = ["h-11", "h-20", "h-29"]
const alturaDaRegua = ["h-30", "h-40", "h-50"]

/**
 * A régua da agenda: cada parcela na data de desconto, num eixo de meses proporcional ao tempo. É só o espelho
 * visual da tabela, que continua sendo o controle acessível da seleção.
 */
export function ReguaAgenda({
  eixo,
  parcelas,
  selecionadas,
}: {
  eixo: Eixo
  parcelas: Parcela[]
  selecionadas: string[]
}) {
  const posicoes = parcelas.map((parcela) => eixo.posicao(parcela.dataDesconto))
  const niveis = niveisDosRotulos(posicoes)
  return (
    <div
      aria-hidden
      data-testid="regua-agenda"
      className={cn(
        "relative mt-4 mb-9 border-b-2 border-foreground select-none",
        alturaDaRegua[Math.max(0, ...niveis)]
      )}
    >
      {/* shortcut: rótulos de mês fixos colidem num eixo de mais de ~12 meses; espaçar os rótulos se a agenda crescer */}
      {eixo.meses.map((mes, indice) => (
        <div
          key={mes.posicao}
          style={{ left: `${mes.posicao}%` }}
          className="absolute bottom-0"
        >
          <span className="absolute -bottom-0.5 h-[18px] w-0.5 bg-foreground" />
          <span
            className={cn(
              "absolute -bottom-7 text-xs font-bold whitespace-nowrap",
              // o último mês fecha o eixo: o rótulo fica à esquerda da marca, dentro do cartão
              indice === eixo.meses.length - 1 && "right-0",
              // tela estreita: um mês sim, outro não, mais o último (as marcas de todos os meses ficam)
              !rotuloNaTelaEstreita(indice, eixo.meses.length) &&
                "max-sm:hidden"
            )}
          >
            {mes.rotulo}
          </span>
        </div>
      ))}
      {eixo.dias.map((posicao) => (
        <span
          key={posicao}
          style={{ left: `${posicao}%` }}
          className="absolute -bottom-0.5 h-2 w-px bg-foreground/25"
        />
      ))}
      <div
        style={{ left: `${eixo.hoje}%` }}
        className="absolute top-5 -bottom-0.5 border-l border-dashed border-brand-navy"
      >
        <span className="absolute -top-[18px] left-1 text-[0.6875rem] font-bold text-brand-navy">
          hoje
        </span>
      </div>
      {parcelas.map((parcela, indice) => {
        const posicao = posicoes[indice]
        const estado = !parcela.elegivel
          ? "inelegivel"
          : selecionadas.includes(parcela.parcelaId)
            ? "selecionada"
            : "elegivel"
        return (
          // largura zero: a haste e o ponto ficam centrados na data, e o rótulo pode se encostar nela perto das bordas
          <div
            key={parcela.parcelaId}
            data-estado={estado}
            style={{ left: `${posicao}%` }}
            className="absolute bottom-0 flex w-0 flex-col items-center"
          >
            {/* acima das hastes e com o fundo do cartão: a haste de uma vizinha próxima passa por trás do rótulo */}
            <div
              className={cn(
                "relative z-10 flex flex-col items-center bg-card px-0.5 whitespace-nowrap",
                posicao < 10 && "items-start self-start",
                posicao > 90 && "items-end self-end"
              )}
            >
              <span
                className={cn(
                  "text-[0.9375rem] font-extrabold",
                  estado === "inelegivel" && "text-muted-foreground"
                )}
              >
                {formatarMoeda(parcela.valor)}
              </span>
              <span className="text-[0.6875rem] text-muted-foreground">
                {formatarData(parcela.dataDesconto).slice(0, 5)} ·{" "}
                {parcela.parcelaId}
              </span>
            </div>
            <span
              className={cn(
                "mt-1.5 w-[3px] rounded-full transition-colors duration-200 motion-reduce:transition-none",
                alturaDaHaste[niveis[indice]],
                marca[estado].haste
              )}
            />
            <span
              className={cn(
                "-mt-0.5 -mb-[10px] size-[18px] shrink-0 rounded-full border-[3px] border-card ring-2 transition-colors duration-200 motion-reduce:transition-none",
                marca[estado].ponto
              )}
            />
          </div>
        )
      })}
    </div>
  )
}
