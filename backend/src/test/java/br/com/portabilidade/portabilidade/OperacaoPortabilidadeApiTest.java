package br.com.portabilidade.portabilidade;

import br.com.portabilidade.ApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class OperacaoPortabilidadeApiTest extends ApiTest {

	private static final String CONTA_DESTINO = """
			{ "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" }""";

	@Test
	void envioAceitoPelaCentralRespondeAcceptedComAOperacaoPendente() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");

		enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody()
				.jsonPath("$.id").isNotEmpty()
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA"))
				.jsonPath("$.eventos[0].dataHora").isNotEmpty()
				.jsonPath("$.eventos[1].dataHora").isNotEmpty()
				.json("""
						{
						  "beneficiarioId": "BEN001",
						  "status": "PENDENTE",
						  "protocolo": "prot-1",
						  "motivoRejeicao": null,
						  "parcelas": [
						    { "parcelaId": "P001", "valor": 150.5 },
						    { "parcelaId": "P002", "valor": 300 }
						  ],
						  "contaDestino": { "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" }
						}""");
	}

	@Test
	void envioLevaACentralOBeneficiarioAAutorizacaoAsParcelasEAConta() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");

		enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO).expectStatus().isAccepted();

		central.verify(1, postRequestedFor(urlEqualTo("/operacoes-portabilidade"))
				.withRequestBody(equalToJson("""
						{
						  "beneficiarioId": "BEN001",
						  "autorizacaoId": "aut-123",
						  "parcelaIds": ["P001"],
						  "contaDestino": { "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" }
						}""")));
	}

	@Test
	void beneficiarioSemAutorizacaoRespondeForbiddenSemChamarACentral() {
		enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO)
				.expectStatus().isForbidden()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.title").isEqualTo("Beneficiário sem autorização");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void envioSemIdempotencyKeyRespondeProblemDetailSemChamarACentral() {
		autorizar("BEN001");
		central.resetRequests();

		api.post().uri("/api/operacoes-portabilidade")
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo("BEN001", List.of("P001"), CONTA_DESTINO))
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.title").isEqualTo("Requisição inválida")
				.jsonPath("$.detail").isEqualTo("O header Idempotency-Key é obrigatório.");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void idempotencyKeyQueNaoEhUuidRespondeProblemDetail() {
		api.post().uri("/api/operacoes-portabilidade")
				.header("Idempotency-Key", "nao-eh-uuid")
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo("BEN001", List.of("P001"), CONTA_DESTINO))
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Requisição inválida");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void contaComFormatoInvalidoRespondeProblemDetailSemChamarACentral() {
		enviar(UUID.randomUUID(), "BEN001", List.of("P001"), """
				{ "banco": "1", "agencia": "1234", "conta": "56789", "digito": "0" }""")
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Requisição inválida");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void envioSemParcelasRespondeProblemDetailSemChamarACentral() {
		enviar(UUID.randomUUID(), "BEN001", List.of(), CONTA_DESTINO)
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void parcelaForaDaAgendaDoBeneficiarioRespondeUnprocessableSemEnviarACentral() {
		autorizar("BEN001");
		agendaDoBen001();

		enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P999"), CONTA_DESTINO)
				.expectStatus().isEqualTo(422)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Parcela fora da agenda")
				.jsonPath("$.detail").isEqualTo("As parcelas [P999] não estão na agenda do beneficiário BEN001.");

		central.verify(0, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
	}

	@Test
	void consultaDevolveAOperacaoComoFoiGravada() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");
		var enviada = enviar(UUID.randomUUID(), "BEN001", List.of("P002", "P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();
		String id = JsonPath.read(enviada, "$.id");

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody().json(enviada, JsonCompareMode.STRICT);
	}

	@Test
	void consultaDeOperacaoInexistenteRespondeNotFound() {
		api.get().uri("/api/operacoes-portabilidade/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Operação não encontrada");
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

	private static void agendaDoBen001() {
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("""
				[
				  { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150.5, "dataDesconto": "2026-01-10", "status": "ABERTA" },
				  { "parcelaId": "P002", "convenioId": "CONV01", "valor": 300, "dataDesconto": "2026-02-10", "status": "ABERTA" }
				]""")));
	}

	private static void centralAceitaOEnvio(String protocolo) {
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "%s" }""".formatted(protocolo)).withStatus(202)));
	}

	private RestTestClient.ResponseSpec enviar(UUID chave, String beneficiarioId, List<String> parcelaIds,
			String contaDestino) {
		return api.post().uri("/api/operacoes-portabilidade")
				.header("Idempotency-Key", chave.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo(beneficiarioId, parcelaIds, contaDestino))
				.exchange();
	}

	private static String corpo(String beneficiarioId, List<String> parcelaIds, String contaDestino) {
		var ids = parcelaIds.stream().map(id -> "\"" + id + "\"").toList();
		return """
				{ "beneficiarioId": "%s", "parcelaIds": [%s], "contaDestino": %s }"""
				.formatted(beneficiarioId, String.join(", ", ids), contaDestino);
	}

}
