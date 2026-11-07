package br.com.portabilidade.autorizacao;

import br.com.portabilidade.central.CentralClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AutorizacaoService {

	private final AutorizacaoRepository repository;
	private final CentralClient central;
	private final JdbcClient jdbc;

	AutorizacaoService(AutorizacaoRepository repository, CentralClient central, JdbcClient jdbc) {
		this.repository = repository;
		this.central = central;
		this.jdbc = jdbc;
	}

	/** Registra a autorização na Central; se o beneficiário já tem uma, devolve a existente sem chamar a Central. */
	@Transactional
	Registro registrar(String beneficiarioId) {
		// lock consultivo até o fim da transação: registros simultâneos do mesmo beneficiário chamam a Central uma vez só
		jdbc.sql("select pg_advisory_xact_lock(hashtext(?))").param(beneficiarioId).query((linha, numero) -> 1).single();
		return repository.findById(beneficiarioId)
				.map(existente -> new Registro(existente, false))
				.orElseGet(() -> {
					var autorizacaoId = central.registrarAutorizacao(beneficiarioId);
					// precisão do timestamptz do Postgres, para a resposta do 201 ser igual à dos 200 seguintes
					var nova = new Autorizacao(beneficiarioId, autorizacaoId, Instant.now().truncatedTo(ChronoUnit.MICROS));
					return new Registro(repository.save(nova), true);
				});
	}

	/** Dados do beneficiário só saem da plataforma com a autorização dele registrada aqui. */
	public void exigirAutorizacao(String beneficiarioId) {
		if (!repository.existsById(beneficiarioId)) {
			throw new BeneficiarioSemAutorizacaoException(beneficiarioId);
		}
	}

	record Registro(Autorizacao autorizacao, boolean nova) {
	}

}
