package br.com.portabilidade.agenda;

/** A agenda só é servida a beneficiários com autorização registrada na plataforma. */
public class BeneficiarioSemAutorizacaoException extends RuntimeException {

	private final String beneficiarioId;

	BeneficiarioSemAutorizacaoException(String beneficiarioId) {
		super("Beneficiário sem autorização: " + beneficiarioId);
		this.beneficiarioId = beneficiarioId;
	}

	public String getBeneficiarioId() {
		return beneficiarioId;
	}

}
