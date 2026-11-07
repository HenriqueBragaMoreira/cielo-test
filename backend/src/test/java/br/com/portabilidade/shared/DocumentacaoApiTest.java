package br.com.portabilidade.shared;

import br.com.portabilidade.ApiTest;
import org.junit.jupiter.api.Test;

class DocumentacaoApiTest extends ApiTest {

	@Test
	void openApiDescreveAApi() {
		api.get().uri("/v3/api-docs")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.info.title").isEqualTo("Portabilidade de Convênios")
				.jsonPath("$.paths['/api/autorizacoes'].post").exists()
				.jsonPath("$.paths['/api/operacoes-portabilidade'].post").exists()
				.jsonPath("$.paths['/api/operacoes-portabilidade/{id}'].get").exists();
	}

	@Test
	void swaggerUiEstaAcessivel() {
		api.get().uri("/swagger-ui/index.html")
				.exchange()
				.expectStatus().isOk();
	}

}
