package br.com.portabilidade.central;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * A Central de Convênios não respondeu: conexão recusada, timeout ou resposta 5xx.
 * Título e detalhe do {@code 503} vêm de {@code messages.properties}.
 */
public class CentralIndisponivelException extends ErrorResponseException {

	CentralIndisponivelException(Throwable causa) {
		super(HttpStatus.SERVICE_UNAVAILABLE, causa);
	}

}
