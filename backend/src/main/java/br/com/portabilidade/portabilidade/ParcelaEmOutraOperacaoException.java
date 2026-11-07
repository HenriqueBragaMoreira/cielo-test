package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.Collection;

/**
 * Parcelas pedidas que já estão em outra operação {@code PENDENTE} ou {@code CONFIRMADA} (trava de parcela, ADR-0005).
 * Textos em {@code messages.properties}.
 */
class ParcelaEmOutraOperacaoException extends ErrorResponseException {

	ParcelaEmOutraOperacaoException(Collection<String> parcelaIds) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatus(HttpStatus.CONFLICT), null, null,
				new Object[] { parcelaIds });
	}

}
