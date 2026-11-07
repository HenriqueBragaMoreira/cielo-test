package br.com.portabilidade.autorizacao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/** Evidência da autorização do beneficiário registrada na Central de Convênios. Uma por beneficiário. */
@Entity
public class Autorizacao implements Persistable<String> {

	@Id
	private String beneficiarioId;

	private String autorizacaoId;

	private Instant autorizadaEm;

	/** O id é atribuído por nós: sem isso, o {@code save} faria um SELECT (merge) antes do INSERT. */
	@Transient
	private boolean nova = true;

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

	@Override
	public String getId() {
		return beneficiarioId;
	}

	@Override
	public boolean isNew() {
		return nova;
	}

	@PostLoad
	@PostPersist
	void marcarPersistida() {
		nova = false;
	}

}
