package br.com.portabilidade.central;

import br.com.portabilidade.ApiTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
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

		verificarAssinatura(central.findAll(postRequestedFor(urlEqualTo("/autorizacoes"))).getFirst(), "/autorizacoes");
	}

	@Test
	void envioDaOperacaoChegaNaCentralComAssinaturaQueConfereComOSegredo() throws Exception {
		central.stubFor(post("/autorizacoes").willReturn(okJson("""
				{ "autorizacaoId": "aut-123" }""").withStatus(201)));
		central.stubFor(get("/beneficiarios/BEN001/agenda").willReturn(okJson("""
				[ { "parcelaId": "P001", "convenioId": "CONV01", "valor": 150, "dataDesconto": "2026-01-10", "status": "ABERTA" } ]""")));
		central.stubFor(post("/operacoes-portabilidade").willReturn(okJson("""
				{ "protocolo": "prot-1" }""").withStatus(202)));
		api.post().uri("/api/autorizacoes")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{ "beneficiarioId": "BEN001" }""")
				.exchange()
				.expectStatus().isCreated();

		api.post().uri("/api/operacoes-portabilidade")
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{ "beneficiarioId": "BEN001", "parcelaIds": ["P001"],
						  "contaDestino": { "banco": "001", "agencia": "1234", "conta": "56789", "digito": "0" } }""")
				.exchange()
				.expectStatus().isAccepted();

		verificarAssinatura(central.findAll(postRequestedFor(urlEqualTo("/operacoes-portabilidade"))).getFirst(),
				"/operacoes-portabilidade");
	}

	/** Remonta o texto canônico a partir do que a Central recebeu: timestamp, método, caminho e corpo. */
	private static void verificarAssinatura(LoggedRequest recebida, String caminho) throws Exception {
		var timestamp = recebida.getHeader("X-Timestamp");
		var assinatura = recebida.getHeader("X-Assinatura");
		assertThat(timestamp).isNotBlank();
		assertThat(assinatura).isNotBlank();
		assertThat(Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), Instant.now()).abs())
				.isLessThan(Duration.ofMinutes(1));

		var canonico = new ByteArrayOutputStream();
		canonico.writeBytes((timestamp + "\n" + recebida.getMethod().getName() + "\n" + caminho + "\n")
				.getBytes(StandardCharsets.UTF_8));
		canonico.writeBytes(recebida.getBody());
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SEGREDO_HMAC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		assertThat(assinatura).isEqualTo(HexFormat.of().formatHex(mac.doFinal(canonico.toByteArray())));
	}

}
