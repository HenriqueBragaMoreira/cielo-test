package br.com.portabilidade.portabilidade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface OperacaoPortabilidadeRepository extends JpaRepository<OperacaoPortabilidade, UUID> {

	/** Com {@code select ... for update}: reenvios concorrentes da mesma chave passam um de cada vez (ADR-0005). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OperacaoPortabilidade> findWithLockByChaveIdempotencia(UUID chaveIdempotencia);

}
