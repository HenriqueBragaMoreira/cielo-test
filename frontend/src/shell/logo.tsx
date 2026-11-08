import { cn } from "cn"

type LogoProps = {
  /** "branca" sobre o azul-marinho da barra; "azul" sobre superfícies claras. */
  variante?: "branca" | "azul"
  className?: string
}

/** Wordmark "cielo" recriado em SVG a partir da referência pública (ADR-0012); o "o" fica em ciano. */
export function Logo({ variante = "branca", className }: LogoProps) {
  return (
    <svg
      role="img"
      aria-label="Cielo"
      viewBox="0 0 82 36"
      fill="none"
      strokeWidth={5}
      strokeLinecap="round"
      className={cn(
        "h-7 w-auto",
        variante === "branca" ? "text-white" : "text-brand-navy",
        className
      )}
    >
      <g stroke="currentColor">
        {/* c */}
        <path d="M17.3 18.7A7.5 7.5 0 1 0 17.3 29.3" />
        {/* i */}
        <path d="M26 16.5V31.5" />
        {/* e */}
        <path d="M33.5 24H48.5A7.5 7.5 0 1 0 47.14 28.3" />
        {/* l */}
        <path d="M56 4.5V31.5" />
      </g>
      <circle cx={26} cy={7} r={3} fill="currentColor" />
      <circle cx={70} cy={24} r={7.5} className="stroke-brand-cyan" />
    </svg>
  )
}
