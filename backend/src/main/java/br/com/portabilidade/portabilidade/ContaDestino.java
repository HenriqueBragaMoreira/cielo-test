package br.com.portabilidade.portabilidade;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Conta de recebimento de destino. Só o formato é validado; a existência da conta, não. */
@Embeddable
public record ContaDestino(
		@NotNull @Pattern(regexp = "\\d{3}") String banco,
		@NotNull @Pattern(regexp = "\\d{4}") String agencia,
		@NotNull @Pattern(regexp = "\\d{1,20}") String conta,
		@NotNull @Pattern(regexp = "\\d") String digito) {
}
