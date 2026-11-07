package br.com.portabilidade.agenda;

import br.com.portabilidade.central.CentralClient;

import java.util.Objects;

/** Se uma parcela pode ser portada e, quando não pode, por quê (ADR-0006). */
public record Elegibilidade(boolean elegivel, String motivoInelegibilidade) {

	private static final Elegibilidade ELEGIVEL = new Elegibilidade(true, null);

	/** Pela agenda da Central; a criação da operação também recusa as parcelas inelegíveis aqui (ADR-0006). */
	public static Elegibilidade de(CentralClient.Parcela parcela) {
		if (!"ABERTA".equals(parcela.status())) {
			return inelegivel("A parcela está com status " + parcela.status() + "; só parcelas ABERTA podem ser portadas.");
		}
		return ELEGIVEL;
	}

	/** Para a agenda: além da Central, a parcela não pode estar numa operação PENDENTE ou CONFIRMADA (ADR-0006). */
	static Elegibilidade de(CentralClient.Parcela parcela, boolean travada) {
		var naCentral = de(parcela);
		if (naCentral.elegivel() && travada) {
			return inelegivel("A parcela já está numa operação de portabilidade pendente ou confirmada.");
		}
		return naCentral;
	}

	private static Elegibilidade inelegivel(String motivo) {
		return new Elegibilidade(false, Objects.requireNonNull(motivo));
	}

}
