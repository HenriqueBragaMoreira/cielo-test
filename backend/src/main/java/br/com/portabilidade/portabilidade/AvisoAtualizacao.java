package br.com.portabilidade.portabilidade;

/**
 * Por que a consulta de uma operação {@code PENDENTE} não trouxe o status atual da Central (ADR-0007). A operação
 * continua {@code PENDENTE}, sem evento nem mudança de estado.
 */
enum AvisoAtualizacao {

	/**
	 * A Central não respondeu (503, conexão recusada ou timeout) ou respondeu fora do contrato; a próxima consulta tenta
	 * de novo.
	 */
	FALHA_NA_ATUALIZACAO,

	/**
	 * A Central respondeu {@code 404}: não conhece mais o protocolo, porque o simulador reiniciou. Sem
	 * {@code Retry-After}, o cliente para de consultar sozinho.
	 */
	PROTOCOLO_NAO_RECONHECIDO

}
