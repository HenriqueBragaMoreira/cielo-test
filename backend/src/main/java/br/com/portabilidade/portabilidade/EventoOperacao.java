package br.com.portabilidade.portabilidade;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Um evento da trilha da operação: só de inserção (ADR-0007). */
@Entity
@Table(name = "operacao_evento")
class EventoOperacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "operacao_id")
	private OperacaoPortabilidade operacao;

	@Enumerated(EnumType.STRING)
	private TipoEvento tipo;

	private Instant dataHora;

	private String detalhe;

	protected EventoOperacao() {
	}

	EventoOperacao(OperacaoPortabilidade operacao, TipoEvento tipo, Instant dataHora, String detalhe) {
		this.operacao = operacao;
		this.tipo = tipo;
		this.dataHora = dataHora;
		this.detalhe = detalhe;
	}

	TipoEvento getTipo() {
		return tipo;
	}

	Instant getDataHora() {
		return dataHora;
	}

	String getDetalhe() {
		return detalhe;
	}

}
