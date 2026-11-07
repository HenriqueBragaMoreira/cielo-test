package br.com.portabilidade.autorizacao;

import br.com.portabilidade.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class AutorizacaoApiTest extends ApiTest {

	@Test
	void autorizacaoNovaRegistraNaCentralERespondeCreated() {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));

		api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{ "beneficiarioId": "BEN001" }""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.beneficiarioId").isEqualTo("BEN001")
				.jsonPath("$.autorizacaoId").isEqualTo("aut-123")
				.jsonPath("$.autorizadaEm").exists();

		central.verify(1, postRequestedFor(urlEqualTo("/autorizacoes"))
				.withRequestBody(equalToJson("""
						{ "beneficiarioId": "BEN001" }""")));
	}

	@Test
	void autorizacaoRepetidaDevolveAExistenteSemChamarACentral() {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));
		var primeira = registrar("BEN001").expectStatus().isCreated()
				.expectBody(String.class).returnResult().getResponseBody();

		registrar("BEN001")
				.expectStatus().isOk()
				.expectBody().json(primeira, JsonCompareMode.STRICT);

		central.verify(1, postRequestedFor(urlEqualTo("/autorizacoes")));
	}

	private RestTestClient.ResponseSpec registrar(String beneficiarioId) {
		return api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("beneficiarioId", beneficiarioId))
				.exchange();
	}

}
