package br.com.portabilidade.autorizacao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/** Evidência da autorização do beneficiário registrada na Central de Convênios. Uma por beneficiário. */
@Entity
public class Autorizacao {

	@Id
	private String beneficiarioId;

	private String autorizacaoId;

	private Instant autorizadaEm;

	protected Autorizacao() {
	}

	Autorizacao(String beneficiarioId, String autorizacaoId, Instant autorizadaEm) {
		this.beneficiarioId = beneficiarioId;
		this.autorizacaoId = autorizacaoId;
		this.autorizadaEm = autorizadaEm;
	}

	public String getBeneficiarioId() {
		return beneficiarioId;
	}

	public String getAutorizacaoId() {
		return autorizacaoId;
	}

	public Instant getAutorizadaEm() {
		return autorizadaEm;
	}

}
