package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/** A {@code Idempotency-Key} já identifica outro pedido (ADR-0005). Textos em {@code messages.properties}. */
class ChaveIdempotenciaReutilizadaException extends ErrorResponseException {

	ChaveIdempotenciaReutilizadaException(UUID chaveIdempotencia) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT), null, null,
				new Object[] { chaveIdempotencia });
	}

}
