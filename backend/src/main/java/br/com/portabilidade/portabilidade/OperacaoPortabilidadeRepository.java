package br.com.portabilidade.portabilidade;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface OperacaoPortabilidadeRepository extends JpaRepository<OperacaoPortabilidade, UUID> {

	/** Com {@code select ... for update}: reenvios concorrentes da mesma chave passam um de cada vez (ADR-0005). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OperacaoPortabilidade> findWithLockByChaveIdempotencia(UUID chaveIdempotencia);

	/** Das parcelas dadas, as travadas numa operação ativa (a coluna {@code ativa} vem do V3). */
	@Query(nativeQuery = true, value = "select parcela_id from operacao_parcela where ativa and parcela_id in (:parcelaIds)")
	Set<String> findParcelasTravadas(Collection<String> parcelaIds);

	/** Das parcelas dadas, as travadas numa operação ativa de outra chave (a coluna {@code ativa} vem do V3). */
	@Query(nativeQuery = true, value = """
			select p.parcela_id from operacao_parcela p join operacao_portabilidade o on o.id = p.operacao_id
			where p.ativa and p.parcela_id in (:parcelaIds) and o.chave_idempotencia <> :chaveIdempotencia
			order by p.parcela_id""")
	List<String> findParcelasEmOutraOperacaoAtiva(Collection<String> parcelaIds, UUID chaveIdempotencia);

}
