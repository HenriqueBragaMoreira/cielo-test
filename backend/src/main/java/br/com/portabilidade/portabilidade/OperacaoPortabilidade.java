package br.com.portabilidade.portabilidade;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pedido para transferir parcelas de um beneficiário para a nova credora. */
@Entity
class OperacaoPortabilidade {

	/** Nosso identificador, não o protocolo: operações em FALHA_ENVIO não têm protocolo (ADR-0005). */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String beneficiarioId;

	@Enumerated(EnumType.STRING)
	private StatusOperacao status;

	private String protocolo;

	private String motivoRejeicao;

	@Embedded
	private ContaDestino contaDestino;

	private UUID chaveIdempotencia;

	private String hashCorpo;

	private Instant criadaEm;

	private Instant atualizadaEm;

	@ElementCollection
	@CollectionTable(name = "operacao_parcela", joinColumns = @JoinColumn(name = "operacao_id"))
	@OrderBy("parcelaId")
	private List<ParcelaOperacao> parcelas = new ArrayList<>();

	@OneToMany(mappedBy = "operacao", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<EventoOperacao> eventos = new ArrayList<>();

	protected OperacaoPortabilidade() {
	}

	/**
	 * Nasce PENDENTE e ainda sem protocolo, para a inserção já reservar a chave e as parcelas antes do envio à Central
	 * (ADR-0005). Esse estado sem protocolo só existe dentro da transação do envio.
	 */
	static OperacaoPortabilidade solicitar(String beneficiarioId, List<ParcelaOperacao> parcelas,
			ContaDestino contaDestino, UUID chaveIdempotencia, String hashCorpo) {
		var operacao = new OperacaoPortabilidade();
		operacao.beneficiarioId = beneficiarioId;
		// mesma ordem do @OrderBy, para a resposta do envio ser igual à das consultas seguintes
		parcelas.stream().sorted(Comparator.comparing(ParcelaOperacao::parcelaId)).forEach(operacao.parcelas::add);
		operacao.contaDestino = contaDestino;
		operacao.chaveIdempotencia = chaveIdempotencia;
		operacao.hashCorpo = hashCorpo;
		operacao.status = StatusOperacao.PENDENTE;
		operacao.criadaEm = agora();
		operacao.registrar(TipoEvento.SOLICITADA, null);
		return operacao;
	}

	/** A Central aceitou o envio, ou o reenvio a partir de FALHA_ENVIO, e atribuiu o protocolo. */
	void registrarEnvio(String protocolo) {
		this.protocolo = protocolo;
		status = StatusOperacao.PENDENTE;
		registrar(TipoEvento.ENVIADA, null);
	}

	/** A Central não respondeu ao envio: a operação pode ser reenviada e libera as parcelas (ADR-0007). */
	void registrarFalhaEnvio() {
		status = StatusOperacao.FALHA_ENVIO;
		registrar(TipoEvento.FALHA_ENVIO, null);
	}

	/** Só uma operação em FALHA_ENVIO é reenviada à Central; nos outros estados, o reenvio só a devolve (ADR-0005). */
	boolean aguardaReenvio() {
		return status == StatusOperacao.FALHA_ENVIO;
	}

	/** O pedido tem o mesmo corpo do que criou esta operação, ou seja, é a mesma intenção (ADR-0005). */
	boolean mesmaIntencao(String hashCorpo) {
		return this.hashCorpo.equals(hashCorpo);
	}

	private void registrar(TipoEvento tipo, String detalhe) {
		var instante = agora();
		atualizadaEm = instante;
		eventos.add(new EventoOperacao(this, tipo, instante, detalhe));
	}

	/** Precisão do timestamptz do Postgres, para a resposta do envio ser igual à das consultas seguintes. */
	private static Instant agora() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	UUID getId() {
		return id;
	}

	String getBeneficiarioId() {
		return beneficiarioId;
	}

	StatusOperacao getStatus() {
		return status;
	}

	String getProtocolo() {
		return protocolo;
	}

	String getMotivoRejeicao() {
		return motivoRejeicao;
	}

	ContaDestino getContaDestino() {
		return contaDestino;
	}

	List<ParcelaOperacao> getParcelas() {
		return parcelas;
	}

	List<EventoOperacao> getEventos() {
		return eventos;
	}

}
