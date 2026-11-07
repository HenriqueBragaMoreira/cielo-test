package br.com.portabilidade.central;

import br.com.portabilidade.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0009: toda requisição à Central sai assinada com HMAC-SHA256. */
class AssinaturaHmacApiTest extends ApiTest {

	@Test
	void autorizacaoChegaNaCentralComAssinaturaQueConfereComOSegredo() throws Exception {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));

		api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{ "beneficiarioId": "BEN001" }""")
				.exchange()
				.expectStatus().isCreated();

		var recebida = central.findAll(postRequestedFor(urlEqualTo("/autorizacoes"))).getFirst();
		var timestamp = recebida.getHeader("X-Timestamp");
		var assinatura = recebida.getHeader("X-Assinatura");
		assertThat(timestamp).isNotBlank();
		assertThat(assinatura).isNotBlank();
		assertThat(Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), Instant.now()).abs())
				.isLessThan(Duration.ofMinutes(1));

		// texto canônico remontado a partir do que a Central recebeu: timestamp \n método \n caminho \n corpo
		var canonico = new ByteArrayOutputStream();
		canonico.writeBytes((timestamp + "\nPOST\n/autorizacoes\n").getBytes(StandardCharsets.UTF_8));
		canonico.writeBytes(recebida.getBody());
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SEGREDO_HMAC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		assertThat(assinatura).isEqualTo(HexFormat.of().formatHex(mac.doFinal(canonico.toByteArray())));
	}

}
