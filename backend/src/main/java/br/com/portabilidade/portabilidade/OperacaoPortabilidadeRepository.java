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

	/**
	 * Histórico (ADR-0013): as 50 mais recentes, com o total e a quantidade numa consulta só, sem N+1. A subconsulta
	 * escolhe as 50 antes de somar, para não agregar as parcelas de todas as operações; o id desempata o instante.
	 */
	@Query("""
			select new br.com.portabilidade.portabilidade.ResumoOperacao(o.id, o.beneficiarioId, o.status,
				count(p.parcelaId), sum(p.valor), o.criadaEm, o.atualizadaEm)
			from OperacaoPortabilidade o join o.parcelas p
			where o.id in (select r.id from OperacaoPortabilidade r order by r.criadaEm desc, r.id desc limit 50)
			group by o.id, o.beneficiarioId, o.status, o.criadaEm, o.atualizadaEm
			order by o.criadaEm desc, o.id desc""")
	List<ResumoOperacao> findHistorico();

}
