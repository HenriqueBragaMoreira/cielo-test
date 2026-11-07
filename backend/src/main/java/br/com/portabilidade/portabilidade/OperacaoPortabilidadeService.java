package br.com.portabilidade.portabilidade;

import br.com.portabilidade.autorizacao.Autorizacao;
import br.com.portabilidade.autorizacao.AutorizacaoService;
import br.com.portabilidade.central.CentralClient;
import br.com.portabilidade.central.CentralIndisponivelException;
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

	OperacaoPortabilidadeService(OperacaoPortabilidadeRepository repository, AutorizacaoService autorizacoes,
			CentralClient central, TransactionTemplate transacao) {
		this.repository = repository;
		this.autorizacoes = autorizacoes;
		this.central = central;
		this.transacao = transacao;
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
			// requisição concorrente com a mesma chave: a inserção esperou no índice único até o commit da outra
			operacao = transacao.execute(status -> repetida(chaveIdempotencia, hashCorpo)).orElseThrow(() -> e);
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
		return repository.findWithLockByChaveIdempotencia(chaveIdempotencia).map(operacao -> {
			if (!operacao.mesmaIntencao(hashCorpo)) {
				throw new ChaveIdempotenciaReutilizadaException(chaveIdempotencia);
			}
			if (operacao.aguardaReenvio()) {
				enviarACentral(operacao, autorizacoes.exigirAutorizacao(operacao.getBeneficiarioId()));
			}
			return Operacao.de(operacao);
		});
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

	/** Só lê o que está gravado; o acompanhamento junto à Central é de outro ticket. */
	@Transactional(readOnly = true)
	Operacao consultar(UUID id) {
		return repository.findById(id).map(Operacao::de).orElseThrow(() -> new OperacaoNaoEncontradaException(id));
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
