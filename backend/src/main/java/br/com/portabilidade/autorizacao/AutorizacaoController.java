package br.com.portabilidade.autorizacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

	/** O tamanho máximo é o da coluna; os espaços das pontas saem antes da validação. */
	record AutorizacaoRequest(@NotBlank @Size(max = 100) String beneficiarioId) {

		AutorizacaoRequest {
			beneficiarioId = beneficiarioId == null ? null : beneficiarioId.strip();
		}

	}

	record AutorizacaoResponse(String beneficiarioId, String autorizacaoId, Instant autorizadaEm) {
	}

}
