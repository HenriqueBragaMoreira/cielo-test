import { Link, type LinkProps } from "@tanstack/react-router"
import { ArrowLeftIcon } from "lucide-react"

/** A saída de cada etapa para a anterior, logo acima do título da tela. */
export function LinkVoltar({
  children,
  ...props
}: LinkProps & { children: string }) {
  return (
    <Link
      {...props}
      className="flex w-fit items-center gap-1.5 text-sm font-semibold text-primary underline-offset-4 hover:underline"
    >
      <ArrowLeftIcon aria-hidden className="size-4" />
      {children}
    </Link>
  )
}
