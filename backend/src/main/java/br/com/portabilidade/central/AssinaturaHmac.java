package br.com.portabilidade.central;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Assina cada requisição à Central com HMAC-SHA256 (ADR-0009) sobre o texto canônico
 * {@code timestamp \n método \n caminho \n corpo}, enviando {@code X-Timestamp} (epoch em segundos)
 * e {@code X-Assinatura} (hex).
 */
class AssinaturaHmac implements ClientHttpRequestInterceptor {

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec chave;

	/** Falha na subida se o segredo estiver vazio ({@link SecretKeySpec} recusa chave vazia). */
	AssinaturaHmac(String segredo) {
		this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		var timestamp = String.valueOf(Instant.now().getEpochSecond());
		request.getHeaders().set("X-Timestamp", timestamp);
		request.getHeaders().set("X-Assinatura", assinar(timestamp + "\n" + request.getMethod().name() + "\n"
				+ request.getURI().getRawPath() + "\n", body));
		return execution.execute(request, body);
	}

	private String assinar(String cabecalho, byte[] corpo) {
		try {
			var mac = Mac.getInstance(ALGORITMO); // Mac não é thread-safe: uma instância por requisição
			mac.init(chave);
			mac.update(cabecalho.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(mac.doFinal(corpo));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 indisponível", e);
		}
	}

}
