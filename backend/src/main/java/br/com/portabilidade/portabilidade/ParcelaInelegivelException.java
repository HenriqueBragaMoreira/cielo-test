package br.com.portabilidade.portabilidade;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.List;

/**
 * Parcelas pedidas que a agenda relida na Central mostra inelegíveis (ADR-0006), cada uma com o motivo. Textos em
 * {@code messages.properties}.
 */
class ParcelaInelegivelException extends ErrorResponseException {

	ParcelaInelegivelException(List<String> parcelasComMotivo) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT), null, null,
				new Object[] { String.join(", ", parcelasComMotivo) });
	}

}
