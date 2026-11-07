package br.com.portabilidade.central;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A aplicação não sobe sem {@code CENTRAL_HMAC_SECRET} (ADR-0009). Carrega o application.properties real
 * e só o adaptador da Central, onde o segredo é exigido, com a resolução estrita de placeholders da aplicação.
 * Pressupõe que o ambiente que roda os testes não exporta CENTRAL_HMAC_SECRET.
 */
class SegredoHmacObrigatorioTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
			.withBean(RestClient.Builder.class, RestClient::builder)
			.withUserConfiguration(CentralClient.class);

	@Test
	void naoSobeSemSegredo() {
		contexto.run(ctx -> assertThat(ctx).hasFailed()
				.getFailure().rootCause().hasMessageContaining("CENTRAL_HMAC_SECRET"));
	}

	@Test
	void naoSobeComSegredoVazio() {
		contexto.withPropertyValues("CENTRAL_HMAC_SECRET=")
				.run(ctx -> assertThat(ctx).hasFailed()
						.getFailure().rootCause().hasMessageContaining("Empty key"));
	}

	@Test
	void sobeComSegredo() {
		contexto.withPropertyValues("CENTRAL_HMAC_SECRET=qualquer")
				.run(ctx -> assertThat(ctx).hasNotFailed());
	}

}
