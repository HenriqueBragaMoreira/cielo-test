package br.com.portabilidade.portabilidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
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
	 * registrada; {@code 422} com parcela fora da agenda do beneficiário. Reenviar com a mesma {@code Idempotency-Key}
	 * e o mesmo corpo devolve a mesma operação (reenviada se estiver em {@code FALHA_ENVIO}); a mesma chave com outro
	 * corpo dá {@code 422}. Central indisponível no envio: {@code 503} com o {@code id} da operação em {@code FALHA_ENVIO}.
	 */
	@PostMapping
	ResponseEntity<OperacaoPortabilidadeService.Operacao> solicitar(
			@RequestHeader("Idempotency-Key") UUID chaveIdempotencia,
			@Valid @RequestBody OperacaoRequest request) {
		return ResponseEntity.accepted().body(service.solicitar(chaveIdempotencia, request.beneficiarioId(),
				request.parcelaIds(), request.contaDestino()));
	}

	/**
	 * Histórico: as 50 operações mais recentes, da mais nova para a mais antiga, lidas só do nosso banco, sem consultar
	 * a Central nem mandar {@code Retry-After} (ADR-0013). Abrir a operação pelo {@code id} é que a atualiza.
	 */
	@GetMapping
	List<ResumoOperacao> listar() {
		return service.listar();
	}

	/**
	 * A operação pelo nosso identificador, não pelo protocolo da Central. Enquanto {@code PENDENTE}, o header
	 * {@code Retry-After} indica quando consultar de novo (ADR-0008), exceto se a Central não reconhece mais o
	 * protocolo: consultar de novo não mudaria nada.
	 */
	@GetMapping("/{id}")
	ResponseEntity<OperacaoPortabilidadeService.Operacao> consultar(@PathVariable UUID id) {
		var operacao = service.consultar(id);
		var resposta = ResponseEntity.ok();
		if (operacao.status() == StatusOperacao.PENDENTE
				&& operacao.avisoAtualizacao() != AvisoAtualizacao.PROTOCOLO_NAO_RECONHECIDO) {
			resposta.header(HttpHeaders.RETRY_AFTER, "2");
		}
		return resposta.body(operacao);
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
