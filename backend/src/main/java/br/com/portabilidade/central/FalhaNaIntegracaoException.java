package br.com.portabilidade.central;

/** A Central de Convênios respondeu, mas não como o contrato prevê: resposta 4xx, corpo ilegível ou vazio. */
public class FalhaNaIntegracaoException extends RuntimeException {

	FalhaNaIntegracaoException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}

}
