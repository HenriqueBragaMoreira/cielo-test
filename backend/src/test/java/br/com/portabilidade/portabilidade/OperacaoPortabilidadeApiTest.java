package br.com.portabilidade.portabilidade;

import br.com.portabilidade.ApiTest;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
	void reenvioComAMesmaChaveEOMesmoCorpoDevolveAMesmaOperacaoSemChamarACentral() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");
		var chave = UUID.randomUUID();
		var primeira = enviar(chave, "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();
		central.resetRequests();

		// a ordem das parcelas não muda a intenção
		enviar(chave, "BEN001", List.of("P002", "P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody().json(primeira, JsonCompareMode.STRICT);

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void mesmaChaveComCorpoDiferenteRespondeUnprocessableSemChamarACentral() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");
		var chave = UUID.randomUUID();
		enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO).expectStatus().isAccepted();
		central.resetRequests();

		enviar(chave, "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isEqualTo(422)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.title").isEqualTo("Chave de idempotência já usada")
				.jsonPath("$.detail").isEqualTo("Este pedido de portabilidade difere do que já foi enviado com a mesma"
						+ " chave de idempotência (" + chave + "). Confira as parcelas e a conta e envie novamente.");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void envioDuploConcorrenteComAMesmaChaveCriaUmaOperacaoEChamaACentralUmaVez() throws Exception {
		autorizar("BEN001");
		agendaDoBen001();
		// a Central demora para a segunda requisição chegar enquanto a primeira ainda está na transação
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "prot-1" }""").withStatus(202).withFixedDelay(500)));
		var chave = UUID.randomUUID();
		var partida = new CountDownLatch(1);
		Callable<String> envio = () -> {
			partida.await();
			return enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO)
					.expectStatus().isAccepted()
					.expectBody(String.class).returnResult().getResponseBody();
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var primeira = executor.submit(envio);
			var segunda = executor.submit(envio);
			partida.countDown();

			String idPrimeira = JsonPath.read(primeira.get(), "$.id");
			String idSegunda = JsonPath.read(segunda.get(), "$.id");
			assertThat(idSegunda).isEqualTo(idPrimeira);
		}
		central.verify(1, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
	}

	static Stream<Arguments> centralIndisponivelNoEnvio() {
		return Stream.of(
				Arguments.of("503", serviceUnavailable()),
				// acima do timeout de leitura dos testes (1 s)
				Arguments.of("timeout", okJson("""
						{ "protocolo": "prot-1" }""").withStatus(202).withFixedDelay(2_000)),
				// a conexão recusada de verdade fica no reenvio, que não lê a agenda antes de enviar
				Arguments.of("conexão interrompida", aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("centralIndisponivelNoEnvio")
	void centralIndisponivelNoEnvioGravaAOperacaoEmFalhaEnvioERespondeServiceUnavailableComOId(String falha,
			ResponseDefinitionBuilder resposta) {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(resposta));

		String id = esperarFalhaNoEnvio(enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO));

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("FALHA_ENVIO")
				.jsonPath("$.protocolo").isEmpty()
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "FALHA_ENVIO"));
	}

	@Test
	void parcelasDeUmaOperacaoEmFalhaEnvioPodemEntrarEmOutraOperacao() {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		String falhou = esperarFalhaNoEnvio(enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO));
		centralAceitaOEnvio("prot-2");

		var outra = enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();

		assertThat(JsonPath.<String>read(outra, "$.id")).isNotEqualTo(falhou);
		assertThat(JsonPath.<String>read(outra, "$.status")).isEqualTo("PENDENTE");
	}

	@Test
	void reenvioComAMesmaChaveAPartirDeFalhaEnvioEnviaDeNovoEDevolveAMesmaOperacaoPendente() {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		var chave = UUID.randomUUID();
		String id = esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO));
		centralAceitaOEnvio("prot-1");
		central.resetRequests();

		var reenviada = enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();

		assertThat(JsonPath.<String>read(reenviada, "$.id")).isEqualTo(id);
		assertThat(JsonPath.<String>read(reenviada, "$.status")).isEqualTo("PENDENTE");
		assertThat(JsonPath.<String>read(reenviada, "$.protocolo")).isEqualTo("prot-1");
		assertThat(JsonPath.<List<String>>read(reenviada, "$.eventos[*].tipo"))
				.containsExactly("SOLICITADA", "FALHA_ENVIO", "ENVIADA");
		central.verify(1, postRequestedFor(urlEqualTo("/operacoes-portabilidade"))
				.withRequestBody(equalToJson("""
						{
						  "beneficiarioId": "BEN001",
						  "autorizacaoId": "aut-123",
						  "parcelaIds": ["P001"],
						  "contaDestino": { "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" }
						}""")));
		central.resetRequests();

		// já PENDENTE: o reenvio só devolve a operação
		enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody().json(reenviada, JsonCompareMode.STRICT);
		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void reenvioComACentralRecusandoAConexaoContinuaEmFalhaEnvio() {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		var chave = UUID.randomUUID();
		String id = esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO));

		central.stop();
		try {
			assertThat(esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO))).isEqualTo(id);
		}
		finally {
			central.start();
		}

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("FALHA_ENVIO")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "FALHA_ENVIO", "FALHA_ENVIO"));
	}

	@Test
	void reenvioConcorrenteAPartirDeFalhaEnvioChamaACentralUmaVez() throws Exception {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		var chave = UUID.randomUUID();
		String id = esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO));
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "prot-1" }""").withStatus(202).withFixedDelay(500)));
		central.resetRequests();
		var partida = new CountDownLatch(1);
		Callable<String> reenvio = () -> {
			partida.await();
			return enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO)
					.expectStatus().isAccepted()
					.expectBody(String.class).returnResult().getResponseBody();
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var primeira = executor.submit(reenvio);
			var segunda = executor.submit(reenvio);
			partida.countDown();

			assertThat(JsonPath.<String>read(primeira.get(), "$.id")).isEqualTo(id);
			assertThat(JsonPath.<String>read(segunda.get(), "$.id")).isEqualTo(id);
		}
		central.verify(1, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
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

	/** O {@code 503} {@code problem+json} de uma operação que ficou em {@code FALHA_ENVIO}; devolve o {@code id} dela. */
	private static String esperarFalhaNoEnvio(RestTestClient.ResponseSpec resposta) {
		var problema = resposta.expectStatus().isEqualTo(503)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody(String.class).returnResult().getResponseBody();
		assertThat(JsonPath.<Integer>read(problema, "$.status")).isEqualTo(503);
		assertThat(JsonPath.<String>read(problema, "$.title")).isEqualTo("Central de Convênios indisponível");
		assertThat(JsonPath.<String>read(problema, "$.detail")).isEqualTo(
				"A Central de Convênios não respondeu e a operação ficou registrada como falha no envio. Tente novamente.");
		String id = JsonPath.read(problema, "$.id");
		assertThat(id).isNotBlank();
		return id;
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
