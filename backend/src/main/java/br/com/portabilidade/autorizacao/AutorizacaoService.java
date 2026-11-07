package br.com.portabilidade.autorizacao;

import br.com.portabilidade.central.CentralClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AutorizacaoService {

	private final AutorizacaoRepository repository;
	private final CentralClient central;

	AutorizacaoService(AutorizacaoRepository repository, CentralClient central) {
		this.repository = repository;
		this.central = central;
	}

	/** Registra a autorização na Central; se o beneficiário já tem uma, devolve a existente sem chamar a Central. */
	Registro registrar(String beneficiarioId) {
		return repository.findById(beneficiarioId)
				.map(existente -> new Registro(existente, false))
				.orElseGet(() -> {
					var autorizacaoId = central.registrarAutorizacao(beneficiarioId);
					// precisão do timestamptz do Postgres, para a resposta do 201 ser igual à dos 200 seguintes
					var nova = new Autorizacao(beneficiarioId, autorizacaoId, Instant.now().truncatedTo(ChronoUnit.MICROS));
					return new Registro(repository.save(nova), true);
				});
	}

	record Registro(Autorizacao autorizacao, boolean nova) {
	}

}
