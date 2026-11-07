package br.com.portabilidade.portabilidade;

/**
 * Por que a consulta de uma operação {@code PENDENTE} não trouxe o status atual da Central (ADR-0007). A operação
 * continua {@code PENDENTE}, sem evento nem mudança de estado, e o cliente segue consultando.
 */
enum AvisoAtualizacao {

	/** A Central não respondeu (503, conexão recusada ou timeout); a próxima consulta tenta de novo. */
	FALHA_NA_ATUALIZACAO,

	/** A Central respondeu {@code 404}: não conhece mais o protocolo, porque o simulador reiniciou. */
	PROTOCOLO_NAO_RECONHECIDO

}
