package br.com.portabilidade.autorizacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/autorizacoes")
class AutorizacaoController {

	private final AutorizacaoService service;

	AutorizacaoController(AutorizacaoService service) {
		this.service = service;
	}

	/** {@code 201} quando a autorização é registrada agora; {@code 200} quando o beneficiário já tinha uma. */
	@PostMapping
	ResponseEntity<AutorizacaoResponse> registrar(@Valid @RequestBody AutorizacaoRequest request) {
		var registro = service.registrar(request.beneficiarioId());
		var autorizacao = registro.autorizacao();
		return ResponseEntity.status(registro.nova() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(new AutorizacaoResponse(autorizacao.getBeneficiarioId(), autorizacao.getAutorizacaoId(), autorizacao.getAutorizadaEm()));
	}

	record AutorizacaoRequest(@NotBlank String beneficiarioId) {
	}

	record AutorizacaoResponse(String beneficiarioId, String autorizacaoId, Instant autorizadaEm) {
	}

}
