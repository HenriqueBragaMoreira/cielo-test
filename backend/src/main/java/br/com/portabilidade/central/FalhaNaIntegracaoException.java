package br.com.portabilidade.central;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * A Central de Convênios respondeu, mas não como o contrato prevê: resposta 4xx, corpo ilegível ou vazio.
 * Título e detalhe do {@code 502} vêm de {@code messages.properties}; a mensagem fica só no log.
 */
public class FalhaNaIntegracaoException extends ErrorResponseException {

	private final String mensagem;

	FalhaNaIntegracaoException(String mensagem, Throwable causa) {
		super(HttpStatus.BAD_GATEWAY, causa);
		this.mensagem = mensagem;
	}

	@Override
	public String getMessage() {
		return mensagem;
	}

}
