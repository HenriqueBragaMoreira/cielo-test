package br.com.portabilidade;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base dos testes na costura da API HTTP (ADR-0010): sobe a aplicação numa porta aleatória,
 * com Postgres real (Testcontainers) e a Central de Convênios substituída por WireMock.
 * Cada teste começa com o banco vazio e o WireMock sem stubs nem requisições registradas.
 */
// timeout de leitura menor que os 5 s de produção, para os testes de timeout da Central não atrasarem a build
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.http.clients.read-timeout=1s")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

	/**
	 * A Central de Convênios falsa, compartilhada por todos os testes (o contexto Spring também é).
	 * A porta é fixa para que {@code central.stop()} simule conexão recusada e {@code central.start()} volte no mesmo endereço.
	 */
	protected static final WireMockServer central = new WireMockServer(wireMockConfig().port(portaLivre())
			// como o simulador (Node http), só HTTP/1.1; com h2c o JDK HttpClient recebe RST_STREAM
			.http2PlainDisabled(true));

	/** Segredo HMAC dos testes (em produção vem de {@code CENTRAL_HMAC_SECRET}). */
	protected static final String SEGREDO_HMAC = "segredo-hmac-de-teste";

	static {
		central.start();
	}

	private static int portaLivre() {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void centralUrl(DynamicPropertyRegistry registry) {
		registry.add("central.url", central::baseUrl);
		registry.add("central.hmac-secret", () -> SEGREDO_HMAC);
	}

	@Autowired
	protected RestTestClient api;

	@Autowired
	private JdbcClient jdbc;

	/** A resposta é o {@code 503} {@code problem+json} de "Central de Convênios indisponível". */
	protected static void esperarCentralIndisponivel(RestTestClient.ResponseSpec resposta) {
		resposta.expectStatus().isEqualTo(503)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.title").isEqualTo("Central de Convênios indisponível")
				.jsonPath("$.detail").isEqualTo("A Central de Convênios não respondeu. Tente novamente mais tarde.");
	}

	/** A resposta é o {@code 502} {@code problem+json} de "Falha na integração com a Central de Convênios". */
	protected static void esperarFalhaNaIntegracao(RestTestClient.ResponseSpec resposta) {
		resposta.expectStatus().isEqualTo(502)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(502)
				.jsonPath("$.title").isEqualTo("Falha na integração com a Central de Convênios")
				.jsonPath("$.detail").isEqualTo("A Central de Convênios deu uma resposta inesperada. Tente novamente mais tarde.");
	}

	@BeforeEach
	void reiniciarEstado() {
		central.resetAll();
		var tabelas = jdbc.sql("select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'")
				.query(String.class)
				.list();
		if (!tabelas.isEmpty()) {
			jdbc.sql("truncate " + String.join(", ", tabelas) + " cascade").update();
		}
	}

}
