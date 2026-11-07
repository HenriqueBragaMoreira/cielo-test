package br.com.portabilidade.central;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Único adaptador HTTP para a Central de Convênios (ADR-0002). */
@Component
public class CentralClient {

	private final RestClient restClient;

	public CentralClient(RestClient.Builder builder, @Value("${central.url}") String centralUrl,
			@Value("${central.hmac-secret}") String segredoHmac) {
		builder.requestInterceptor(new AssinaturaHmac(segredoHmac));
		this.restClient = builder.baseUrl(centralUrl).build();
	}

	/** Registra a autorização do beneficiário na Central e devolve o {@code autorizacaoId} atribuído por ela. */
	public String registrarAutorizacao(String beneficiarioId) {
		return chamar(() -> restClient.post()
				.uri("/autorizacoes")
				.body(new AutorizacaoRequest(beneficiarioId))
				.retrieve()
				.body(AutorizacaoResponse.class))
				.autorizacaoId();
	}

	/** Parcelas futuras do beneficiário, como a Central as informa. */
	public List<Parcela> consultarAgenda(String beneficiarioId) {
		return chamar(() -> restClient.get()
				.uri("/beneficiarios/{beneficiarioId}/agenda", beneficiarioId)
				.retrieve()
				.body(new ParameterizedTypeReference<List<Parcela>>() {
				}));
	}

	/** Envia a operação de portabilidade à Central e devolve o protocolo atribuído por ela. */
	public String enviarOperacao(EnvioOperacao envio) {
		return chamar(() -> restClient.post()
				.uri("/operacoes-portabilidade")
				.body(envio)
				.retrieve()
				.body(EnvioOperacaoResponse.class))
				.protocolo();
	}

	/**
	 * Consulta a operação pelo protocolo. Vazio enquanto a Central ainda decide ({@code 204}, sem corpo), que não é
	 * falha.
	 */
	public Optional<DecisaoOperacao> consultarOperacao(String protocolo) {
		var resposta = chamar(() -> restClient.get()
				.uri("/operacoes-portabilidade/{protocolo}", protocolo)
				.retrieve()
				.toEntity(DecisaoOperacao.class));
		if (resposta.getStatusCode().isSameCodeAs(HttpStatus.NO_CONTENT)) {
			return Optional.empty();
		}
		if (resposta.getBody() == null) {
			throw new FalhaNaIntegracaoException("Resposta da Central de Convênios sem corpo", null);
		}
		return Optional.of(resposta.getBody());
	}

	/**
	 * Traduz conexão recusada, timeout ({@link ResourceAccessException}) e 5xx em {@link CentralIndisponivelException};
	 * qualquer outra falha da chamada (4xx, corpo ilegível ou vazio) em {@link FalhaNaIntegracaoException}.
	 */
	private static <T> T chamar(Supplier<T> chamada) {
		T resposta;
		try {
			resposta = chamada.get();
		} catch (ResourceAccessException | HttpServerErrorException e) {
			throw new CentralIndisponivelException(e);
		} catch (RestClientException e) {
			throw new FalhaNaIntegracaoException("Falha na chamada à Central de Convênios", e);
		}
		if (resposta == null) {
			throw new FalhaNaIntegracaoException("Resposta da Central de Convênios sem corpo", null);
		}
		return resposta;
	}

	/** Decisão da Central sobre a operação: {@code CONFIRMADA}, ou {@code REJEITADA} com o motivo. */
	public record DecisaoOperacao(String status, String motivoRejeicao) {

		public DecisaoOperacao {
			if (!"CONFIRMADA".equals(status) && !"REJEITADA".equals(status)) {
				throw new IllegalArgumentException("Decisão da Central com status desconhecido: " + status);
			}
		}

	}

	public record Parcela(String parcelaId, String convenioId, BigDecimal valor, LocalDate dataDesconto, String status) {
	}

	public record EnvioOperacao(String beneficiarioId, String autorizacaoId, List<String> parcelaIds,
			ContaDestino contaDestino) {
	}

	public record ContaDestino(String banco, String agencia, String conta, String digito) {
	}

	private record EnvioOperacaoResponse(String protocolo) {

		EnvioOperacaoResponse {
			if (protocolo == null) {
				throw new IllegalArgumentException("Resposta da Central sem protocolo");
			}
		}

	}

	private record AutorizacaoRequest(String beneficiarioId) {
	}

	private record AutorizacaoResponse(String autorizacaoId) {

		AutorizacaoResponse {
			if (autorizacaoId == null) {
				throw new IllegalArgumentException("Resposta da Central sem autorizacaoId");
			}
		}

	}

}
