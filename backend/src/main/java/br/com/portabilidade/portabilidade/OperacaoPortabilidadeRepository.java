package br.com.portabilidade.portabilidade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OperacaoPortabilidadeRepository extends JpaRepository<OperacaoPortabilidade, UUID> {

	Optional<OperacaoPortabilidade> findByChaveIdempotencia(UUID chaveIdempotencia);

}
