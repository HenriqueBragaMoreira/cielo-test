package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.List;

/** Parcelas pedidas que a Central não tem na agenda do beneficiário (ADR-0006). Textos em {@code messages.properties}. */
class ParcelaForaDaAgendaException extends ErrorResponseException {

	ParcelaForaDaAgendaException(String beneficiarioId, List<String> parcelaIds) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT), null, null,
				new Object[] { parcelaIds, beneficiarioId });
	}

}
