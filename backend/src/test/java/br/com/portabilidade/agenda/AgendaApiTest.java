package br.com.portabilidade.agenda;

import br.com.portabilidade.ApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
						"O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda ou portar parcelas.");

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
	void parcelaEmOperacaoPendenteOuConfirmadaApareceInelegivelComOMotivo() {
		autorizar("BEN001");
		agendaComP001EP002Abertas();
		var id = portar("P001", "prot-1");

		esperarElegibilidade(false, true);

		centralDecide("prot-1", "CONFIRMADA");
		esperarStatus(id, "CONFIRMADA");
		esperarElegibilidade(false, true);
	}

	@Test
	void parcelaDeOperacaoRejeitadaVoltaASerElegivel() {
		autorizar("BEN001");
		agendaComP001EP002Abertas();
		var id = portar("P001", "prot-1");

		centralDecide("prot-1", "REJEITADA");
		esperarStatus(id, "REJEITADA");

		esperarElegibilidade(true, true);
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

	@Test
	void respostaDaCentralSemCorpoDaFalhaNaIntegracao() {
		autorizar("BEN001");
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(aResponse().withStatus(200)));

		esperarFalhaNaIntegracao(consultarAgenda("BEN001"));
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

	private static void agendaComP001EP002Abertas() {
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("""
				[
				  { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150.5, "dataDesconto": "2026-01-10", "status": "ABERTA" },
				  { "parcelaId": "P002", "convenioId": "CONV01", "valor": 300, "dataDesconto": "2026-02-10", "status": "ABERTA" }
				]""")));
	}

	/** Uma operação aceita pela Central com a parcela dada; devolve o {@code id} dela. */
	private String portar(String parcelaId, String protocolo) {
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "%s" }""".formatted(protocolo)).withStatus(202)));
		var enviada = api.post().uri("/api/operacoes-portabilidade")
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{ "beneficiarioId": "BEN001", "parcelaIds": ["%s"],
						  "contaDestino": { "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" } }"""
						.formatted(parcelaId))
				.exchange()
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();
		return JsonPath.read(enviada, "$.id");
	}

	private static void centralDecide(String protocolo, String status) {
		central.stubFor(get("/operacoes-portabilidade/" + protocolo).willReturn(okJson("""
				{ "status": "%s", "motivoRejeicao": null }""".formatted(status))));
	}

	private void esperarStatus(String operacaoId, String status) {
		api.get().uri("/api/operacoes-portabilidade/{id}", operacaoId)
				.exchange()
				.expectBody().jsonPath("$.status").isEqualTo(status);
	}

	private void esperarElegibilidade(boolean p001, boolean p002) {
		consultarAgenda("BEN001")
				.expectStatus().isOk()
				.expectBody().json("""
						[
						  { "parcelaId": "P001", "elegivel": %s, "motivoInelegibilidade": %s },
						  { "parcelaId": "P002", "elegivel": %s, "motivoInelegibilidade": %s }
						]""".formatted(p001, motivo(p001), p002, motivo(p002)));
	}

	private static String motivo(boolean elegivel) {
		return elegivel ? "null" : "\"A parcela já está numa operação de portabilidade pendente ou confirmada.\"";
	}

	private RestTestClient.ResponseSpec consultarAgenda(String beneficiarioId) {
		return api.get().uri("/api/beneficiarios/{id}/agenda", beneficiarioId).exchange();
	}

}
