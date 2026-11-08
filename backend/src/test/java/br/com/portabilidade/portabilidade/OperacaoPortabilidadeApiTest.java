package br.com.portabilidade.portabilidade;

import br.com.portabilidade.ApiTest;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OperacaoPortabilidadeApiTest extends ApiTest {

	private static final String CONTA_DESTINO = """
			{ "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" }""";

	@Autowired
	private JdbcClient jdbc;

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

	@Test
	void envioDuploConcorrenteComAMesmaChaveEACentralIndisponivelChamaACentralUmaVez() throws Exception {
		autorizar("BEN001");
		agendaDoBen001();
		// a segunda requisição chega enquanto a primeira espera a Central; a falha da primeira não é reenviada por ela
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable().withFixedDelay(500)));
		var chave = UUID.randomUUID();
		var partida = new CountDownLatch(1);
		Callable<String> envio = () -> {
			partida.await();
			return esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO));
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var primeira = executor.submit(envio);
			var segunda = executor.submit(envio);
			partida.countDown();

			assertThat(segunda.get()).isEqualTo(primeira.get());
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
	void parcelaEmOperacaoAtivaComOutraChaveRespondeConflictSemEnviarACentral() {
		operacaoPendente("prot-1");
		central.resetRequests();

		esperarParcelaEmOutraOperacao(enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P002"), CONTA_DESTINO),
				"[P001]");

		central.verify(0, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
	}

	@Test
	void parcelaDeOperacaoConfirmadaNaoPodeSerPortadaDeNovo() {
		var id = operacaoPendente("prot-1");
		centralDecide("prot-1", """
				{ "status": "CONFIRMADA", "motivoRejeicao": null }""");
		api.get().uri("/api/operacoes-portabilidade/{id}", id).exchange().expectBody().jsonPath("$.status")
				.isEqualTo("CONFIRMADA");
		central.resetRequests();

		esperarParcelaEmOutraOperacao(enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO), "[P001]");

		central.verify(0, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
	}

	@Test
	void parcelaDeOperacaoRejeitadaPodeSerPortadaDeNovo() {
		var id = operacaoPendente("prot-1");
		centralDecide("prot-1", """
				{ "status": "REJEITADA", "motivoRejeicao": "Beneficiário atingiu o limite de margem consignável" }""");
		api.get().uri("/api/operacoes-portabilidade/{id}", id).exchange().expectBody().jsonPath("$.status")
				.isEqualTo("REJEITADA");
		centralAceitaOEnvio("prot-2");

		enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody()
				.jsonPath("$.id").value(String.class, nova -> assertThat(nova).isNotEqualTo(id))
				.jsonPath("$.status").isEqualTo("PENDENTE")
				.jsonPath("$.protocolo").isEqualTo("prot-2");
	}

	@Test
	void reenvioAPartirDeFalhaEnvioComAParcelaJaEmOutraOperacaoRespondeConflictSemEnviarACentral() {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		var chave = UUID.randomUUID();
		String id = esperarFalhaNoEnvio(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO));
		centralAceitaOEnvio("prot-2");
		enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO).expectStatus().isAccepted();
		central.resetRequests();

		esperarParcelaEmOutraOperacao(enviar(chave, "BEN001", List.of("P001"), CONTA_DESTINO), "[P001]");

		central.verify(0, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectBody()
				.jsonPath("$.status").isEqualTo("FALHA_ENVIO")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "FALHA_ENVIO"));
	}

	@Test
	void envioConcorrenteDaMesmaParcelaComChavesDiferentesCriaUmaOperacaoEDaConflictNaOutra() throws Exception {
		autorizar("BEN001");
		agendaDoBen001();
		// a demora mantém a primeira transação aberta com a parcela travada quando a segunda tenta inseri-la
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "prot-1" }""").withStatus(202).withFixedDelay(500)));
		var partida = new CountDownLatch(1);
		Callable<Integer> envio = () -> {
			partida.await();
			return enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO)
					.returnResult().getStatus().value();
		};

		try (var executor = Executors.newFixedThreadPool(2)) {
			var primeira = executor.submit(envio);
			var segunda = executor.submit(envio);
			partida.countDown();

			assertThat(List.of(primeira.get(), segunda.get())).containsExactlyInAnyOrder(202, 409);
		}
		central.verify(1, postRequestedFor(urlEqualTo("/operacoes-portabilidade")));
	}

	@Test
	void parcelaInelegivelNaAgendaRespondeUnprocessableSemEnviarACentral() {
		autorizar("BEN001");
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("""
				[
				  { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150.5, "dataDesconto": "2026-01-10", "status": "ABERTA" },
				  { "parcelaId": "P002", "convenioId": "CONV01", "valor": 300, "dataDesconto": "2026-02-10", "status": "QUITADA" }
				]""")));

		enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isEqualTo(422)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Parcela inelegível")
				.jsonPath("$.detail").isEqualTo("Estas parcelas não podem mais ser portadas: P002 (A parcela está com"
						+ " status QUITADA; só parcelas ABERTA podem ser portadas.). Atualize a agenda e envie novamente.");

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
		centralAindaDecide("prot-1");

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

	@Test
	void consultaDuranteAEsperaDaCentralContinuaPendenteComRetryAfter() {
		var id = operacaoPendente("prot-1");
		centralAindaDecide("prot-1");

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("Retry-After", "2")
				.expectBody()
				.jsonPath("$.status").isEqualTo("PENDENTE")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA"));

		central.verify(1, getRequestedFor(urlEqualTo("/operacoes-portabilidade/prot-1")));
	}

	@Test
	void confirmacaoDaCentralDeixaAOperacaoConfirmadaComOEventoFinal() {
		var id = operacaoPendente("prot-1");
		centralDecide("prot-1", """
				{ "status": "CONFIRMADA", "motivoRejeicao": null }""");

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().doesNotExist("Retry-After")
				.expectBody()
				.jsonPath("$.status").isEqualTo("CONFIRMADA")
				.jsonPath("$.motivoRejeicao").isEmpty()
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA", "CONFIRMADA"))
				.jsonPath("$.eventos[2].dataHora").isNotEmpty();
	}

	@Test
	void rejeicaoDaCentralDeixaAOperacaoRejeitadaComOMotivoNoEventoFinal() {
		var id = operacaoPendente("prot-1");
		centralDecide("prot-1", """
				{ "status": "REJEITADA", "motivoRejeicao": "Beneficiário atingiu o limite de margem consignável" }""");

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().doesNotExist("Retry-After")
				.expectBody()
				.jsonPath("$.status").isEqualTo("REJEITADA")
				.jsonPath("$.motivoRejeicao").isEqualTo("Beneficiário atingiu o limite de margem consignável")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA", "REJEITADA"))
				.jsonPath("$.eventos[2].detalhe").isEqualTo("Beneficiário atingiu o limite de margem consignável");
	}

	@Test
	void motivoDeRejeicaoLongoFicaGravadoInteiro() {
		var id = operacaoPendente("prot-1");
		// a Central não limita o tamanho do motivo
		var motivo = "Motivo extenso. ".repeat(100);
		centralDecide("prot-1", """
				{ "status": "REJEITADA", "motivoRejeicao": "%s" }""".formatted(motivo));

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("REJEITADA")
				.jsonPath("$.motivoRejeicao").isEqualTo(motivo)
				.jsonPath("$.eventos[2].detalhe").isEqualTo(motivo);
	}

	@Test
	void consultasConcorrentesRegistramOEventoFinalUmaVezSo() throws Exception {
		var id = operacaoPendente("prot-1");
		// a demora faz todas as consultas lerem PENDENTE antes de qualquer uma registrar a decisão
		central.stubFor(get("/operacoes-portabilidade/prot-1").willReturn(okJson("""
				{ "status": "CONFIRMADA", "motivoRejeicao": null }""").withFixedDelay(300)));

		try (var executor = Executors.newFixedThreadPool(4)) {
			var consultas = IntStream.range(0, 4)
					.mapToObj(i -> executor.submit(() -> api.get().uri("/api/operacoes-portabilidade/{id}", id)
							.exchange()
							.expectStatus().isOk()))
					.toList();
			for (var consulta : consultas) {
				consulta.get();
			}
		}

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("CONFIRMADA")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA", "CONFIRMADA"));
	}

	@Test
	void operacaoEmEstadoFinalNaoConsultaACentralDeNovo() {
		var id = operacaoPendente("prot-1");
		centralDecide("prot-1", """
				{ "status": "CONFIRMADA", "motivoRejeicao": null }""");
		api.get().uri("/api/operacoes-portabilidade/{id}", id).exchange().expectStatus().isOk();

		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("CONFIRMADA")
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA", "CONFIRMADA"));

		central.verify(1, getRequestedFor(urlEqualTo("/operacoes-portabilidade/prot-1")));
	}

	static Stream<Arguments> respostaInesperadaNaConsulta() {
		return Stream.of(
				Arguments.of("status desconhecido", okJson("""
						{ "status": "EM_ANALISE", "motivoRejeicao": null }""")),
				Arguments.of("corpo ilegível", okJson("não é json")),
				Arguments.of("4xx que não é 404", aResponse().withStatus(400)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("respostaInesperadaNaConsulta")
	void respostaInesperadaDaCentralNaConsultaContinuaPendenteComAvisoDeFalhaNaAtualizacao(String falha,
			ResponseDefinitionBuilder resposta) {
		var id = operacaoPendente("prot-1");
		central.stubFor(get("/operacoes-portabilidade/prot-1").willReturn(resposta));

		esperarPendenteComAviso(id, "FALHA_NA_ATUALIZACAO", true);
	}

	@Test
	void centralIndisponivelNaConsultaContinuaPendenteComAvisoDeFalhaNaAtualizacao() {
		var id = operacaoPendente("prot-1");
		central.stubFor(get("/operacoes-portabilidade/prot-1").willReturn(serviceUnavailable()));

		esperarPendenteComAviso(id, "FALHA_NA_ATUALIZACAO", true);
	}

	@Test
	void timeoutNaConsultaContinuaPendenteComAvisoDeFalhaNaAtualizacao() {
		var id = operacaoPendente("prot-1");
		// além do read-timeout de 1 s dos testes
		central.stubFor(get("/operacoes-portabilidade/prot-1").willReturn(okJson("""
				{ "status": "CONFIRMADA", "motivoRejeicao": null }""").withFixedDelay(2_000)));

		esperarPendenteComAviso(id, "FALHA_NA_ATUALIZACAO", true);
	}

	@Test
	void protocoloNaoReconhecidoPelaCentralContinuaPendenteComAvisoExplicitoESemRetryAfter() {
		var id = operacaoPendente("prot-1");
		central.stubFor(get("/operacoes-portabilidade/prot-1").willReturn(notFound()));

		// a Central não vai reconhecer o protocolo na próxima consulta: o cliente para de consultar sozinho
		esperarPendenteComAviso(id, "PROTOCOLO_NAO_RECONHECIDO", false);
	}

	/**
	 * Sem evento novo nem mudança de estado; com {@code Retry-After} só quando vale a pena o cliente seguir
	 * consultando.
	 */
	private void esperarPendenteComAviso(String id, String aviso, boolean comRetryAfter) {
		var resposta = api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectStatus().isOk();
		if (comRetryAfter) {
			resposta.expectHeader().valueEquals("Retry-After", "2");
		}
		else {
			resposta.expectHeader().doesNotExist("Retry-After");
		}
		resposta.expectBody()
				.jsonPath("$.status").isEqualTo("PENDENTE")
				.jsonPath("$.protocolo").isEqualTo("prot-1")
				.jsonPath("$.avisoAtualizacao").isEqualTo(aviso)
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA"));

		// a falha não ficou gravada: quando a Central volta a responder, o aviso some
		centralAindaDecide("prot-1");
		api.get().uri("/api/operacoes-portabilidade/{id}", id)
				.exchange()
				.expectBody()
				.jsonPath("$.status").isEqualTo("PENDENTE")
				.jsonPath("$.avisoAtualizacao").isEmpty()
				.jsonPath("$.eventos[*].tipo").isEqualTo(List.of("SOLICITADA", "ENVIADA"));
	}

	@Test
	void historicoSemOperacoesRespondeListaVazia() {
		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectBody().json("[]", JsonCompareMode.STRICT);
	}

	@Test
	void historicoTrazOResumoDeCadaOperacaoComTotalEQuantidadeDeParcelas() {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio("prot-1");
		var enviada = enviar(UUID.randomUUID(), "BEN001", List.of("P001", "P002"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();

		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(1)
				.jsonPath("$[0].criadaEm").isNotEmpty()
				.jsonPath("$[0].atualizadaEm").isNotEmpty()
				.json("""
						[
						  {
						    "id": "%s",
						    "beneficiarioId": "BEN001",
						    "status": "PENDENTE",
						    "quantidadeParcelas": 2,
						    "valorTotal": 450.5
						  }
						]""".formatted(JsonPath.<String>read(enviada, "$.id")));
	}

	@Test
	void historicoListaUmaOperacaoPendenteSemConsultarACentralNemMandarRetryAfter() {
		var id = operacaoPendente("prot-1");
		central.resetRequests();

		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().doesNotExist("Retry-After")
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(id)
				.jsonPath("$[0].status").isEqualTo("PENDENTE");

		central.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void historicoIncluiOperacaoEmFalhaEnvio() {
		autorizar("BEN001");
		agendaDoBen001();
		central.stubFor(post("/operacoes-portabilidade").willReturn(serviceUnavailable()));
		String id = esperarFalhaNoEnvio(enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO));

		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[*].id").isEqualTo(List.of(id))
				.jsonPath("$[0].status").isEqualTo("FALHA_ENVIO");
	}

	@Test
	void historicoVemDaOperacaoMaisRecenteParaAMaisAntiga() {
		var t0 = Instant.parse("2026-01-10T12:00:00Z");
		var meio = gravarOperacao("BEN-MEIO", t0.plusSeconds(3_600));
		var antiga = gravarOperacao("BEN-ANTIGA", t0);
		var recente = gravarOperacao("BEN-RECENTE", t0.plusSeconds(7_200));

		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[*].id").isEqualTo(List.of(recente.toString(), meio.toString(), antiga.toString()));
	}

	@Test
	void historicoSeLimitaAs50OperacoesMaisRecentes() {
		var t0 = Instant.parse("2026-01-10T12:00:00Z");
		IntStream.range(0, 51).forEach(i -> gravarOperacao("BEN-" + i, t0.plusSeconds(60L * i)));

		api.get().uri("/api/operacoes-portabilidade")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(50)
				.jsonPath("$[0].beneficiarioId").isEqualTo("BEN-50")
				.jsonPath("$[49].beneficiarioId").isEqualTo("BEN-1");
	}

	/** Grava direto no banco uma operação confirmada com uma parcela, criada no instante dado; devolve o {@code id}. */
	private UUID gravarOperacao(String beneficiarioId, Instant criadaEm) {
		var id = UUID.randomUUID();
		jdbc.sql("""
				insert into operacao_portabilidade (id, beneficiario_id, status, banco, agencia, conta, digito,
				    chave_idempotencia, hash_corpo, criada_em, atualizada_em)
				values (?, ?, 'CONFIRMADA', '001', '1234', '56789', '0', ?, 'hash', ?, ?)""")
				.params(id, beneficiarioId, UUID.randomUUID(), Timestamp.from(criadaEm), Timestamp.from(criadaEm))
				.update();
		jdbc.sql("insert into operacao_parcela (operacao_id, parcela_id, valor) values (?, ?, 100)")
				.params(id, "P-" + id)
				.update();
		return id;
	}

	/** Uma operação aceita pela Central, PENDENTE com o protocolo dado. */
	private String operacaoPendente(String protocolo) {
		autorizar("BEN001");
		agendaDoBen001();
		centralAceitaOEnvio(protocolo);
		var enviada = enviar(UUID.randomUUID(), "BEN001", List.of("P001"), CONTA_DESTINO)
				.expectStatus().isAccepted()
				.expectBody(String.class).returnResult().getResponseBody();
		return JsonPath.read(enviada, "$.id");
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

	/** O {@code 204} da Central: a operação ainda está sendo decidida. */
	private static void centralAindaDecide(String protocolo) {
		central.stubFor(get("/operacoes-portabilidade/" + protocolo)
				.willReturn(aResponse().withStatus(204).withHeader("Retry-After", "2")));
	}

	private static void centralDecide(String protocolo, String decisao) {
		central.stubFor(get("/operacoes-portabilidade/" + protocolo).willReturn(okJson(decisao)));
	}

	/** A resposta é o {@code 409} {@code problem+json} da trava de parcela (ADR-0005), com as parcelas em conflito. */
	private static void esperarParcelaEmOutraOperacao(RestTestClient.ResponseSpec resposta, String parcelaIds) {
		resposta.expectStatus().isEqualTo(409)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(409)
				.jsonPath("$.title").isEqualTo("Parcela em outra operação")
				.jsonPath("$.detail").isEqualTo("As parcelas " + parcelaIds + " já estão numa operação de portabilidade"
						+ " pendente ou confirmada. Retire-as da seleção e envie novamente.");
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
