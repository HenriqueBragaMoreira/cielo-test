package br.com.portabilidade.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo erro da API sai como {@code application/problem+json}, com mensagem em português (ADR-0007).
 * Erros do Spring MVC e de domínio ({@link ErrorResponseException}) já trazem o status; o texto deles
 * vem de {@code messages.properties}. Aqui ficam só o log e o {@code 500} dos erros não previstos.
 */
@RestControllerAdvice
class ProblemDetailHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailHandler.class);

	@Override
	protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.warn("Falha numa dependência da API", e);
		}
		return super.handleErrorResponseException(e, headers, status, request);
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
