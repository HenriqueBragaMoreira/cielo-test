package br.com.portabilidade.portabilidade;

import br.com.portabilidade.autorizacao.AutorizacaoService;
import br.com.portabilidade.central.CentralClient;
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

	OperacaoPortabilidadeService(OperacaoPortabilidadeRepository repository, AutorizacaoService autorizacoes,
			CentralClient central) {
		this.repository = repository;
		this.autorizacoes = autorizacoes;
		this.central = central;
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
