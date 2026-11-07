package br.com.portabilidade.autorizacao;

import br.com.portabilidade.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
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

	@Test
	void centralRespondendo503DaCentralIndisponivel() {
		central.stubFor(post("/autorizacoes").willReturn(serviceUnavailable()));

		esperarCentralIndisponivel(registrar("BEN001"));
	}

	@Test
	void conexaoRecusadaPelaCentralDaCentralIndisponivel() {
		central.stop();
		try {
			esperarCentralIndisponivel(registrar("BEN001"));
		} finally {
			central.start();
		}
	}

	@Test
	void timeoutDeLeituraDaCentralDaCentralIndisponivel() {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201).withFixedDelay(2_000)));

		esperarCentralIndisponivel(registrar("BEN001"));
	}

	@Test
	void falhaDaCentralNaoGravaAutorizacao() {
		central.stubFor(post("/autorizacoes").willReturn(serviceUnavailable()));
		registrar("BEN001").expectStatus().isEqualTo(503);

		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));
		registrar("BEN001").expectStatus().isCreated();

		central.verify(2, postRequestedFor(urlEqualTo("/autorizacoes")));
	}

	@Test
	void beneficiarioIdVazioRespondeProblemDetail() {
		registrar(" ")
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.title").isEqualTo("Requisição inválida")
				.jsonPath("$.detail").isEqualTo("Verifique os campos informados e tente novamente.");

		central.verify(0, postRequestedFor(urlEqualTo("/autorizacoes")));
	}

	@Test
	void erroInesperadoRespondeProblemDetail() {
		central.stubFor(post("/autorizacoes").willReturn(badRequest()));

		registrar("BEN001")
				.expectStatus().isEqualTo(500)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Erro inesperado")
				.jsonPath("$.detail").isEqualTo("Não foi possível concluir a requisição. Tente novamente mais tarde.");
	}

	private static void esperarCentralIndisponivel(RestTestClient.ResponseSpec resposta) {
		resposta.expectStatus().isEqualTo(503)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.title").isEqualTo("Central de Convênios indisponível")
				.jsonPath("$.detail").isEqualTo("A Central de Convênios não respondeu. Tente novamente mais tarde.");
	}

	private RestTestClient.ResponseSpec registrar(String beneficiarioId) {
		return api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("beneficiarioId", beneficiarioId))
				.exchange();
	}

}
