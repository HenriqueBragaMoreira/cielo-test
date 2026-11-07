package br.com.portabilidade.autorizacao;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Dados do beneficiário só são servidos com autorização registrada na plataforma.
 * Título e detalhe do {@code 403} vêm de {@code messages.properties}.
 */
public class BeneficiarioSemAutorizacaoException extends ErrorResponseException {

	BeneficiarioSemAutorizacaoException(String beneficiarioId) {
		super(HttpStatus.FORBIDDEN, ProblemDetail.forStatus(HttpStatus.FORBIDDEN), null, null,
				new Object[] { beneficiarioId });
	}

}
