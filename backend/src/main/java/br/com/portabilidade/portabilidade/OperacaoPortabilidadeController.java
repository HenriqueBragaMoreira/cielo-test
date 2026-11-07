package br.com.portabilidade.portabilidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/operacoes-portabilidade")
class OperacaoPortabilidadeController {

	private final OperacaoPortabilidadeService service;

	OperacaoPortabilidadeController(OperacaoPortabilidadeService service) {
		this.service = service;
	}

	/**
	 * Cria a operação e a envia à Central: {@code 202} com a operação {@code PENDENTE}. {@code 403} sem autorização
	 * registrada; {@code 422} com parcela fora da agenda do beneficiário.
	 */
	@PostMapping
	ResponseEntity<OperacaoPortabilidadeService.Operacao> solicitar(
			@RequestHeader("Idempotency-Key") UUID chaveIdempotencia,
			@Valid @RequestBody OperacaoRequest request) {
		return ResponseEntity.accepted().body(service.solicitar(chaveIdempotencia, request.beneficiarioId(),
				request.parcelaIds(), request.contaDestino()));
	}

	/** A operação pelo nosso identificador, não pelo protocolo da Central. */
	@GetMapping("/{id}")
	OperacaoPortabilidadeService.Operacao consultar(@PathVariable UUID id) {
		return service.consultar(id);
	}

	/** Parcelas repetidas contam uma vez só; os tamanhos máximos são os das colunas. */
	record OperacaoRequest(@NotBlank @Size(max = 100) String beneficiarioId,
			@NotEmpty List<@NotBlank @Size(max = 100) String> parcelaIds,
			@NotNull @Valid ContaDestino contaDestino) {

		OperacaoRequest {
			beneficiarioId = beneficiarioId == null ? null : beneficiarioId.strip();
			parcelaIds = parcelaIds == null ? null : parcelaIds.stream().distinct().toList();
		}

	}

}
