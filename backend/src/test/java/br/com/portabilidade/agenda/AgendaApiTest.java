package br.com.portabilidade.agenda;

import br.com.portabilidade.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;

class AgendaApiTest extends ApiTest {

	@Test
	void beneficiarioSemAutorizacaoRespondeForbiddenSemChamarACentral() {
		consultarAgenda("BEN001")
				.expectStatus().isForbidden()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.title").isEqualTo("Beneficiário sem autorização")
				.jsonPath("$.detail").isEqualTo(
						"O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda.");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void beneficiarioAutorizadoRecebeAsParcelasComAElegibilidade() {
		autorizar("BEN001");
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("""
				[
				  { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150.5, "dataDesconto": "2026-01-10", "status": "ABERTA" },
				  { "parcelaId": "P002", "convenioId": "CONV02", "valor": 300, "dataDesconto": "2026-02-15", "status": "QUITADA" }
				]""")));

		consultarAgenda("BEN001")
				.expectStatus().isOk()
				.expectBody().json("""
						[
						  { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150.5, "dataDesconto": "2026-01-10",
						    "status": "ABERTA", "elegivel": true, "motivoInelegibilidade": null },
						  { "parcelaId": "P002", "convenioId": "CONV02", "valor": 300, "dataDesconto": "2026-02-15",
						    "status": "QUITADA", "elegivel": false,
						    "motivoInelegibilidade": "A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas." }
						]""", JsonCompareMode.STRICT);
	}

	@Test
	void agendaVaziaDevolveListaVazia() {
		autorizar("BEN_ERRO");
		central.stubFor(get("/beneficiarios/BEN_ERRO/agenda").willReturn(okJson("[]")));

		consultarAgenda("BEN_ERRO")
				.expectStatus().isOk()
				.expectBody().json("[]", JsonCompareMode.STRICT);
	}

	@Test
	void centralRespondendo503DaCentralIndisponivel() {
		autorizar("BEN001");
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(serviceUnavailable()));

		esperarCentralIndisponivel(consultarAgenda("BEN001"));
	}

	@Test
	void conexaoRecusadaPelaCentralDaCentralIndisponivel() {
		autorizar("BEN001");
		central.stop();
		try {
			esperarCentralIndisponivel(consultarAgenda("BEN001"));
		} finally {
			central.start();
		}
	}

	@Test
	void timeoutDeLeituraDaCentralDaCentralIndisponivel() {
		autorizar("BEN001");
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("[]").withFixedDelay(2_000)));

		esperarCentralIndisponivel(consultarAgenda("BEN001"));
	}

	private void autorizar(String beneficiarioId) {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));
		api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("beneficiarioId", beneficiarioId))
				.exchange()
				.expectStatus().isCreated();
	}

	private RestTestClient.ResponseSpec consultarAgenda(String beneficiarioId) {
		return api.get().uri("/api/beneficiarios/{id}/agenda", beneficiarioId).exchange();
	}

}
