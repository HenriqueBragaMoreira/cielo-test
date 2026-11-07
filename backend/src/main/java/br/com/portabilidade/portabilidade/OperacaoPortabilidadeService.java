package br.com.portabilidade.portabilidade;

import br.com.portabilidade.agenda.Elegibilidade;
import br.com.portabilidade.agenda.TravaDeParcelas;
import br.com.portabilidade.autorizacao.Autorizacao;
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
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class OperacaoPortabilidadeService implements TravaDeParcelas {

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
	 * a Central, a não ser que ela esteja em {@code FALHA_ENVIO}: aí a mesma operação é reenviada. A mesma chave com
	 * outro corpo é recusada com {@code 422} (ADR-0005). Se a Central não responder ao envio, a operação fica gravada em
	 * {@code FALHA_ENVIO} e a resposta é o {@code 503} com o {@code id} dela (ADR-0007).
	 */
	Operacao solicitar(UUID chaveIdempotencia, String beneficiarioId, List<String> parcelaIds, ContaDestino conta) {
		var hashCorpo = hashDoCorpo(beneficiarioId, parcelaIds, conta);
		Operacao operacao;
		try {
			operacao = transacao.execute(status -> repetida(chaveIdempotencia, hashCorpo)
					.orElseGet(() -> enviar(chaveIdempotencia, hashCorpo, beneficiarioId, parcelaIds, conta)));
		}
		catch (DataIntegrityViolationException e) {
			// um índice único esperou o commit de outra requisição: a da mesma chave devolve a operação dela como está,
			// sem reenviar uma FALHA_ENVIO (seria retry automático, ADR-0007); a de outra chave que travou alguma destas
			// parcelas (ADR-0005) é um conflito
			operacao = transacao.execute(status -> {
				var emOutraOperacao = repository.findParcelasEmOutraOperacaoAtiva(parcelaIds, chaveIdempotencia);
				if (!emOutraOperacao.isEmpty()) {
					throw new ParcelaEmOutraOperacaoException(emOutraOperacao);
				}
				return existente(chaveIdempotencia, hashCorpo).map(Operacao::de);
			}).orElseThrow(() -> e);
		}
		// a falha já está gravada; só a resposta é de erro
		if (operacao.status() == StatusOperacao.FALHA_ENVIO) {
			throw new FalhaNoEnvioException(operacao.id());
		}
		return operacao;
	}

	/**
	 * A operação já gravada com esta chave, se houver, reenviada se estiver em {@code FALHA_ENVIO}; com outro corpo, a
	 * chave foi reutilizada. A trava na linha faz um reenvio concorrente esperar este terminar e não enviar de novo.
	 */
	private Optional<Operacao> repetida(UUID chaveIdempotencia, String hashCorpo) {
		return existente(chaveIdempotencia, hashCorpo).map(operacao -> {
			if (operacao.aguardaReenvio()) {
				// a reserva vai ao banco antes do envio: parcela travada por outra operação dá 409 sem chamar a Central.
				// Sem reler a agenda: a operação é a mesma, com os valores lidos na criação
				operacao.reservarParaReenvio();
				repository.flush();
				enviarACentral(operacao, autorizacoes.exigirAutorizacao(operacao.getBeneficiarioId()));
			}
			return Operacao.de(operacao);
		});
	}

	/** A operação já gravada com esta chave, travada, se houver; com outro corpo, a chave foi reutilizada. */
	private Optional<OperacaoPortabilidade> existente(UUID chaveIdempotencia, String hashCorpo) {
		var operacao = repository.findWithLockByChaveIdempotencia(chaveIdempotencia);
		if (operacao.isPresent() && !operacao.get().mesmaIntencao(hashCorpo)) {
			throw new ChaveIdempotenciaReutilizadaException(chaveIdempotencia);
		}
		return operacao;
	}

	/**
	 * Grava a operação, envia à Central e grava o resultado na transação de {@link #solicitar} (ADR-0005), a partir do
	 * evento {@code SOLICITADA}. Os valores das parcelas vêm da agenda da Central, não do cliente (ADR-0006).
	 */
	private Operacao enviar(UUID chaveIdempotencia, String hashCorpo, String beneficiarioId, List<String> parcelaIds,
			ContaDestino conta) {
		var autorizacao = autorizacoes.exigirAutorizacao(beneficiarioId);
		var parcelas = lerDaAgenda(beneficiarioId, parcelaIds);
		var operacao = repository.saveAndFlush(OperacaoPortabilidade.solicitar(beneficiarioId, parcelas, conta,
				chaveIdempotencia, hashCorpo));
		enviarACentral(operacao, autorizacao);
		return Operacao.de(operacao);
	}

	/**
	 * Envia a operação à Central, sem retry (ADR-0007): {@code ENVIADA} com o protocolo, ou {@code FALHA_ENVIO} se ela
	 * não responder. A falha é gravada, não lançada, para a transação fazer commit dela (ADR-0005).
	 */
	private void enviarACentral(OperacaoPortabilidade operacao, Autorizacao autorizacao) {
		var conta = operacao.getContaDestino();
		var envio = new CentralClient.EnvioOperacao(operacao.getBeneficiarioId(), autorizacao.getAutorizacaoId(),
				operacao.getParcelas().stream().map(ParcelaOperacao::parcelaId).toList(),
				new CentralClient.ContaDestino(conta.banco(), conta.agencia(), conta.conta(), conta.digito()));
		String protocolo;
		try {
			protocolo = central.enviarOperacao(envio);
		}
		catch (CentralIndisponivelException e) {
			// um timeout pode ter sido aceito pela Central: o reenvio deixaria esse protocolo órfão (limitação do ADR-0005)
			log.warn("Central indisponível no envio da operação {}", operacao.getId(), e);
			operacao.registrarFalhaEnvio();
			return;
		}
		operacao.registrarEnvio(protocolo);
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

	@Override
	public Set<String> travadas(Collection<String> parcelaIds) {
		// sem parcelas não há o que consultar, e "in ()" nem é SQL válido
		return parcelaIds.isEmpty() ? Set.of() : repository.findParcelasTravadas(parcelaIds);
	}

	private List<ParcelaOperacao> lerDaAgenda(String beneficiarioId, List<String> parcelaIds) {
		var agenda = central.consultarAgenda(beneficiarioId).stream()
				.collect(Collectors.toMap(CentralClient.Parcela::parcelaId, Function.identity()));
		var foraDaAgenda = parcelaIds.stream().filter(id -> !agenda.containsKey(id)).toList();
		if (!foraDaAgenda.isEmpty()) {
			throw new ParcelaForaDaAgendaException(beneficiarioId, foraDaAgenda);
		}
		// só a elegibilidade da Central: a trava de parcela fica no banco e dá 409 (ADR-0005)
		var inelegiveis = parcelaIds.stream()
				.map(id -> Map.entry(id, Elegibilidade.de(agenda.get(id))))
				.filter(parcela -> !parcela.getValue().elegivel())
				.map(parcela -> parcela.getKey() + " (" + parcela.getValue().motivoInelegibilidade() + ")")
				.toList();
		if (!inelegiveis.isEmpty()) {
			throw new ParcelaInelegivelException(inelegiveis);
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
