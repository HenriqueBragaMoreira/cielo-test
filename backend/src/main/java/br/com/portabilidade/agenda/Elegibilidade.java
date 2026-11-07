package br.com.portabilidade.agenda;

import br.com.portabilidade.central.CentralClient;

import java.util.Objects;

/** Se uma parcela pode ser portada e, quando não pode, por quê (ADR-0006). */
record Elegibilidade(boolean elegivel, String motivoInelegibilidade) {

	private static final Elegibilidade ELEGIVEL = new Elegibilidade(true, null);

	/** O PRD de portabilidade acrescenta aqui "não está numa operação PENDENTE ou CONFIRMADA". */
	static Elegibilidade de(CentralClient.Parcela parcela) {
		if (!"ABERTA".equals(parcela.status())) {
			return inelegivel("A parcela está com status " + parcela.status() + "; só parcelas ABERTA podem ser portadas.");
		}
		return ELEGIVEL;
	}

	private static Elegibilidade inelegivel(String motivo) {
		return new Elegibilidade(false, Objects.requireNonNull(motivo));
	}

}
