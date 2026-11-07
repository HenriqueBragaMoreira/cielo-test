package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** Título e detalhe do {@code 404} vêm de {@code messages.properties}. */
class OperacaoNaoEncontradaException extends ErrorResponseException {

	OperacaoNaoEncontradaException(UUID id) {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatus(HttpStatus.NOT_FOUND), null, null, new Object[] { id });
	}

}
