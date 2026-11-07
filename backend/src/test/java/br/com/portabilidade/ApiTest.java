package br.com.portabilidade;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base dos testes na costura da API HTTP (ADR-0010): sobe a aplicação numa porta aleatória,
 * com Postgres real (Testcontainers) e a Central de Convênios substituída por WireMock.
 * Cada teste começa com o banco vazio e o WireMock sem stubs nem requisições registradas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

	/** A Central de Convênios falsa, compartilhada por todos os testes (o contexto Spring também é). */
	protected static final WireMockServer central = new WireMockServer(wireMockConfig().dynamicPort()
			// como o simulador (Node http), só HTTP/1.1; com h2c o JDK HttpClient recebe RST_STREAM
			.http2PlainDisabled(true));

	static {
		central.start();
	}

	@DynamicPropertySource
	static void centralUrl(DynamicPropertyRegistry registry) {
		registry.add("central.url", central::baseUrl);
	}

	@Autowired
	protected RestTestClient api;

	@Autowired
	private JdbcClient jdbc;

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
