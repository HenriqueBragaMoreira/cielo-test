package br.com.portabilidade.central;

/**
 * A Central de Convênios respondeu {@code 404} ao protocolo: ela não conhece mais a operação. Só acontece se o simulador
 * reiniciar, porque ele guarda as operações em memória (ADR-0007).
 */
public class ProtocoloNaoReconhecidoException extends RuntimeException {

	ProtocoloNaoReconhecidoException(String protocolo) {
		super("A Central de Convênios não reconhece o protocolo " + protocolo);
	}

}
