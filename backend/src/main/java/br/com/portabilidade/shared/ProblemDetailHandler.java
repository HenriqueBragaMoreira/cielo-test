package br.com.portabilidade.shared;

import br.com.portabilidade.agenda.BeneficiarioSemAutorizacaoException;
import br.com.portabilidade.central.CentralIndisponivelException;
import br.com.portabilidade.central.FalhaNaIntegracaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo erro da API sai como {@code application/problem+json}, com mensagem em português (ADR-0007).
 * Os erros do Spring MVC herdados de {@link ResponseEntityExceptionHandler} têm o texto em {@code messages.properties}.
 */
@RestControllerAdvice
class ProblemDetailHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailHandler.class);

	@ExceptionHandler
	ProblemDetail centralIndisponivel(CentralIndisponivelException e) {
		log.warn("Central de Convênios indisponível", e);
		var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"A Central de Convênios não respondeu. Tente novamente mais tarde.");
		problema.setTitle("Central de Convênios indisponível");
		return problema;
	}

	@ExceptionHandler
	ProblemDetail falhaNaIntegracao(FalhaNaIntegracaoException e) {
		log.error("Falha na integração com a Central de Convênios", e);
		var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"A Central de Convênios deu uma resposta inesperada. Tente novamente mais tarde.");
		problema.setTitle("Falha na integração com a Central de Convênios");
		return problema;
	}

	@ExceptionHandler
	ProblemDetail beneficiarioSemAutorizacao(BeneficiarioSemAutorizacaoException e) {
		var problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "O beneficiário " + e.getBeneficiarioId()
				+ " não tem autorização registrada. Registre a autorização antes de consultar a agenda.");
		problema.setTitle("Beneficiário sem autorização");
		return problema;
	}

	/** Qualquer outro erro: 500 sem expor detalhes internos ao cliente. */
	@ExceptionHandler
	ProblemDetail erroInesperado(Exception e) {
		log.error("Erro inesperado", e);
		var problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Não foi possível concluir a requisição. Tente novamente mais tarde.");
		problema.setTitle("Erro inesperado");
		return problema;
	}

}
