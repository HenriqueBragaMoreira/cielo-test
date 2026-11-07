package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.UUID;

/**
 * A Central não respondeu ao envio e a operação ficou gravada em {@code FALHA_ENVIO}: o {@code 503} leva o {@code id}
 * dela, que pode ser reenviada com a mesma {@code Idempotency-Key} (ADR-0005, ADR-0007). Textos em
 * {@code messages.properties}.
 */
class FalhaNoEnvioException extends ErrorResponseException {

	FalhaNoEnvioException(UUID operacaoId) {
		super(HttpStatus.SERVICE_UNAVAILABLE, comId(operacaoId), null);
	}

	private static ProblemDetail comId(UUID operacaoId) {
		var problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
		problema.setProperty("id", operacaoId);
		return problema;
	}

}
