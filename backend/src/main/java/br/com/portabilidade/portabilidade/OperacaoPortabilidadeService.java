package br.com.portabilidade.portabilidade;

import br.com.portabilidade.autorizacao.AutorizacaoService;
import br.com.portabilidade.central.CentralClient;
import br.com.portabilidade.central.CentralIndisponivelException;
import br.com.portabilidade.central.ProtocoloNaoReconhecidoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class OperacaoPortabilidadeService {

	private static final Logger log = LoggerFactory.getLogger(OperacaoPortabilidadeService.class);

	private final OperacaoPortabilidadeRepository repository;
	private final AutorizacaoService autorizacoes;
	private final CentralClient central;
	private final TransactionTemplate transacao;
	private final EntityManager entityManager;

	OperacaoPortabilidadeService(OperacaoPortabilidadeRepository repository, AutorizacaoService autorizacoes,
			CentralClient central, TransactionTemplate transacao, EntityManager entityManager) {
		this.repository = repository;
		this.autorizacoes = autorizacoes;
		this.central = central;
		this.transacao = transacao;
		this.entityManager = entityManager;
	}

	/**
	 * Reenvio da mesma intenção (mesma {@code Idempotency-Key} e mesmo corpo) devolve a operação já gravada sem chamar
	 * a Central; a mesma chave com outro corpo é recusada com {@code 422} (ADR-0005).
	 */
	Operacao solicitar(UUID chaveIdempotencia, String beneficiarioId, List<String> parcelaIds, ContaDestino conta) {
		var hashCorpo = hashDoCorpo(beneficiarioId, parcelaIds, conta);
		try {
			return transacao.execute(status -> repetida(chaveIdempotencia, hashCorpo)
					.orElseGet(() -> enviar(chaveIdempotencia, hashCorpo, beneficiarioId, parcelaIds, conta)));
		}
		catch (DataIntegrityViolationException e) {
			// requisição concorrente com a mesma chave: a inserção esperou no índice único até o commit da outra
			return transacao.execute(status -> repetida(chaveIdempotencia, hashCorpo)).orElseThrow(() -> e);
		}
	}

	/** A operação já gravada com esta chave, se houver; com outro corpo, a chave foi reutilizada. */
	private Optional<Operacao> repetida(UUID chaveIdempotencia, String hashCorpo) {
		return repository.findByChaveIdempotencia(chaveIdempotencia).map(operacao -> {
			if (!operacao.mesmaIntencao(hashCorpo)) {
				throw new ChaveIdempotenciaReutilizadaException(chaveIdempotencia);
			}
			return Operacao.de(operacao);
		});
	}

	/**
	 * Grava a operação, envia à Central e grava o resultado na transação de {@link #solicitar} (ADR-0005), com os eventos
	 * {@code SOLICITADA} e {@code ENVIADA}. Os valores das parcelas vêm da agenda da Central, não do cliente (ADR-0006).
	 */
	private Operacao enviar(UUID chaveIdempotencia, String hashCorpo, String beneficiarioId, List<String> parcelaIds,
			ContaDestino conta) {
		var autorizacao = autorizacoes.exigirAutorizacao(beneficiarioId);
		var parcelas = lerDaAgenda(beneficiarioId, parcelaIds);
		var operacao = repository.saveAndFlush(OperacaoPortabilidade.solicitar(beneficiarioId, parcelas, conta,
				chaveIdempotencia, hashCorpo));

		var protocolo = central.enviarOperacao(new CentralClient.EnvioOperacao(beneficiarioId,
				autorizacao.getAutorizacaoId(), parcelaIds,
				new CentralClient.ContaDestino(conta.banco(), conta.agencia(), conta.conta(), conta.digito())));
		operacao.registrarEnvio(protocolo);
		return Operacao.de(operacao);
	}

	/**
	 * Acompanhamento (ADR-0008): uma operação {@code PENDENTE} consulta a Central a cada leitura e, se ela já decidiu,
	 * registra a transição e o evento final. Uma operação em estado final não consulta a Central de novo. Se a Central
	 * não responder ou não reconhecer o protocolo, a operação continua {@code PENDENTE}, sem evento, e a resposta leva o
	 * {@link AvisoAtualizacao} (ADR-0007).
	 */
	@Transactional
	Operacao consultar(UUID id) {
		var operacao = repository.findById(id).orElseThrow(() -> new OperacaoNaoEncontradaException(id));
		if (operacao.getStatus() != StatusOperacao.PENDENTE) {
			return Operacao.de(operacao);
		}
		try {
			central.consultarOperacao(operacao.getProtocolo()).ifPresent(decisao -> {
				// atualização condicional (ADR-0007): relê a linha travada e só decide se ela ainda estiver PENDENTE,
				// para consultas concorrentes registrarem a transição e o evento final uma única vez
				entityManager.refresh(operacao, LockModeType.PESSIMISTIC_WRITE);
				if (operacao.getStatus() == StatusOperacao.PENDENTE) {
					operacao.registrarDecisao(StatusOperacao.valueOf(decisao.status()), decisao.motivoRejeicao());
				}
			});
			return Operacao.de(operacao);
		}
		catch (CentralIndisponivelException e) {
			log.warn("Não foi possível atualizar a operação {}: {}", id, e.getCause().toString());
			return Operacao.de(operacao, AvisoAtualizacao.FALHA_NA_ATUALIZACAO);
		}
		catch (ProtocoloNaoReconhecidoException e) {
			log.warn("Operação {}: {}", id, e.getMessage());
			return Operacao.de(operacao, AvisoAtualizacao.PROTOCOLO_NAO_RECONHECIDO);
		}
	}

	private List<ParcelaOperacao> lerDaAgenda(String beneficiarioId, List<String> parcelaIds) {
		var agenda = central.consultarAgenda(beneficiarioId).stream()
				.collect(Collectors.toMap(CentralClient.Parcela::parcelaId, Function.identity()));
		var foraDaAgenda = parcelaIds.stream().filter(id -> !agenda.containsKey(id)).toList();
		if (!foraDaAgenda.isEmpty()) {
			throw new ParcelaForaDaAgendaException(beneficiarioId, foraDaAgenda);
		}
		return parcelaIds.stream().map(id -> new ParcelaOperacao(id, agenda.get(id).valor())).toList();
	}

	/** Identifica a intenção do corpo para a deduplicação por {@code Idempotency-Key} (ADR-0005). */
	private static String hashDoCorpo(String beneficiarioId, List<String> parcelaIds, ContaDestino conta) {
		// parcelas ordenadas: a mesma seleção em outra ordem é a mesma intenção
		var canonico = String.join("\n", beneficiarioId, String.join(",", parcelaIds.stream().sorted().toList()),
				conta.banco(), conta.agencia(),
				conta.conta(), conta.digito());
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}

	/** {@code avisoAtualizacao} é nulo, exceto quando a consulta à Central falhou (ver {@link #consultar}). */
	record Operacao(UUID id, String beneficiarioId, StatusOperacao status, String protocolo, String motivoRejeicao,
			List<ParcelaOperacao> parcelas, ContaDestino contaDestino, List<Evento> eventos,
			AvisoAtualizacao avisoAtualizacao) {

		static Operacao de(OperacaoPortabilidade operacao) {
			return de(operacao, null);
		}

		static Operacao de(OperacaoPortabilidade operacao, AvisoAtualizacao aviso) {
			return new Operacao(operacao.getId(), operacao.getBeneficiarioId(), operacao.getStatus(),
					operacao.getProtocolo(), operacao.getMotivoRejeicao(), List.copyOf(operacao.getParcelas()),
					operacao.getContaDestino(),
					operacao.getEventos().stream().map(e -> new Evento(e.getTipo(), e.getDataHora(), e.getDetalhe())).toList(),
					aviso);
		}

	}

	record Evento(TipoEvento tipo, Instant dataHora, String detalhe) {
	}

}
