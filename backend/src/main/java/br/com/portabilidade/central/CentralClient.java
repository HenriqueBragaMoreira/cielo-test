package br.com.portabilidade.central;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
				.body(AutorizacaoResponse.class)
				.autorizacaoId());
	}

	/** Parcelas futuras do beneficiário, como a Central as informa. */
	public List<Parcela> consultarAgenda(String beneficiarioId) {
		return chamar(() -> restClient.get()
				.uri("/beneficiarios/{beneficiarioId}/agenda", beneficiarioId)
				.retrieve()
				.body(new ParameterizedTypeReference<List<Parcela>>() {
				}));
	}

	/** Traduz conexão recusada, timeout ({@link ResourceAccessException}) e 5xx em {@link CentralIndisponivelException}. */
	private static <T> T chamar(Supplier<T> chamada) {
		try {
			return chamada.get();
		} catch (ResourceAccessException | HttpServerErrorException e) {
			throw new CentralIndisponivelException(e);
		}
	}

	public record Parcela(String parcelaId, String convenioId, BigDecimal valor, LocalDate dataDesconto, String status) {
	}

	private record AutorizacaoRequest(String beneficiarioId) {
	}

	private record AutorizacaoResponse(String autorizacaoId) {
	}

}
