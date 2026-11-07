package br.com.portabilidade.portabilidade;

import br.com.portabilidade.autorizacao.AutorizacaoService;
import br.com.portabilidade.central.CentralClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class OperacaoPortabilidadeService {

	private final OperacaoPortabilidadeRepository repository;
	private final AutorizacaoService autorizacoes;
	private final CentralClient central;
	private final EntityManager entityManager;

	OperacaoPortabilidadeService(OperacaoPortabilidadeRepository repository, AutorizacaoService autorizacoes,
			CentralClient central, EntityManager entityManager) {
		this.repository = repository;
		this.autorizacoes = autorizacoes;
		this.central = central;
		this.entityManager = entityManager;
	}

	/**
	 * Grava a operação, envia à Central e grava o resultado numa única transação (ADR-0005), com os eventos
	 * {@code SOLICITADA} e {@code ENVIADA}. Os valores das parcelas vêm da agenda da Central, não do cliente (ADR-0006).
	 */
	@Transactional
	Operacao solicitar(UUID chaveIdempotencia, String beneficiarioId, List<String> parcelaIds, ContaDestino conta) {
		var autorizacao = autorizacoes.exigirAutorizacao(beneficiarioId);
		var parcelas = lerDaAgenda(beneficiarioId, parcelaIds);
		var operacao = repository.saveAndFlush(OperacaoPortabilidade.solicitar(beneficiarioId, parcelas, conta,
				chaveIdempotencia, hashDoCorpo(beneficiarioId, parcelaIds, conta)));

		var protocolo = central.enviarOperacao(new CentralClient.EnvioOperacao(beneficiarioId,
				autorizacao.getAutorizacaoId(), parcelaIds,
				new CentralClient.ContaDestino(conta.banco(), conta.agencia(), conta.conta(), conta.digito())));
		operacao.registrarEnvio(protocolo);
		return Operacao.de(operacao);
	}

	/**
	 * Acompanhamento (ADR-0008): uma operação {@code PENDENTE} consulta a Central a cada leitura e, se ela já decidiu,
	 * registra a transição e o evento final. Uma operação em estado final não consulta a Central de novo.
	 */
	@Transactional
	Operacao consultar(UUID id) {
		var operacao = repository.findById(id).orElseThrow(() -> new OperacaoNaoEncontradaException(id));
		if (operacao.getStatus() == StatusOperacao.PENDENTE) {
			central.consultarOperacao(operacao.getProtocolo()).ifPresent(decisao -> {
				// atualização condicional (ADR-0007): relê a linha travada e só decide se ela ainda estiver PENDENTE,
				// para consultas concorrentes registrarem a transição e o evento final uma única vez
				entityManager.refresh(operacao, LockModeType.PESSIMISTIC_WRITE);
				if (operacao.getStatus() == StatusOperacao.PENDENTE) {
					operacao.registrarDecisao(StatusOperacao.valueOf(decisao.status()), decisao.motivoRejeicao());
				}
			});
		}
		return Operacao.de(operacao);
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
		var canonico = String.join("\n", beneficiarioId, String.join(",", parcelaIds), conta.banco(), conta.agencia(),
				conta.conta(), conta.digito());
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível", e);
		}
	}

	record Operacao(UUID id, String beneficiarioId, StatusOperacao status, String protocolo, String motivoRejeicao,
			List<ParcelaOperacao> parcelas, ContaDestino contaDestino, List<Evento> eventos) {

		static Operacao de(OperacaoPortabilidade operacao) {
			return new Operacao(operacao.getId(), operacao.getBeneficiarioId(), operacao.getStatus(),
					operacao.getProtocolo(), operacao.getMotivoRejeicao(), List.copyOf(operacao.getParcelas()),
					operacao.getContaDestino(),
					operacao.getEventos().stream().map(e -> new Evento(e.getTipo(), e.getDataHora(), e.getDetalhe())).toList());
		}

	}

	record Evento(TipoEvento tipo, Instant dataHora, String detalhe) {
	}

}
