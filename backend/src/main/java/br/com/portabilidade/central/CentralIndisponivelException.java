package br.com.portabilidade.central;

/** A Central de Convênios não respondeu: conexão recusada, timeout ou resposta 5xx. */
public class CentralIndisponivelException extends RuntimeException {

	CentralIndisponivelException(Throwable causa) {
		super("Central de Convênios indisponível", causa);
	}

}
