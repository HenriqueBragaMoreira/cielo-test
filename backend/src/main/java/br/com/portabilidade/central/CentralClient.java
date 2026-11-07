package br.com.portabilidade.central;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Único adaptador HTTP para a Central de Convênios (ADR-0002). */
@Component
public class CentralClient {

	private final RestClient restClient;

	public CentralClient(RestClient.Builder builder, @Value("${central.url}") String centralUrl) {
		this.restClient = builder.baseUrl(centralUrl).build();
	}

	/** Registra a autorização do beneficiário na Central e devolve o {@code autorizacaoId} atribuído por ela. */
	public String registrarAutorizacao(String beneficiarioId) {
		return restClient.post()
				.uri("/autorizacoes")
				.body(new AutorizacaoRequest(beneficiarioId))
				.retrieve()
				.body(AutorizacaoResponse.class)
				.autorizacaoId();
	}

	private record AutorizacaoRequest(String beneficiarioId) {
	}

	private record AutorizacaoResponse(String autorizacaoId) {
	}

}
