package br.com.portabilidade.portabilidade;

/**
 * Estados da operação de portabilidade e suas transições (ADR-0007): criação → {@code PENDENTE} ou
 * {@code FALHA_ENVIO}; {@code FALHA_ENVIO} → {@code PENDENTE} ou {@code FALHA_ENVIO} (reenvio); {@code PENDENTE} →
 * {@code CONFIRMADA} ou {@code REJEITADA}. {@code CONFIRMADA} e {@code REJEITADA} são finais.
 */
public enum StatusOperacao {
	PENDENTE, CONFIRMADA, REJEITADA, FALHA_ENVIO;

	/** Estados em que uma operação pode ser criada. */
	boolean podeSerInicial() {
		return this == PENDENTE || this == FALHA_ENVIO;
	}

	boolean podeIrPara(StatusOperacao destino) {
		return switch (this) {
			case PENDENTE -> destino == CONFIRMADA || destino == REJEITADA;
			case FALHA_ENVIO -> destino == PENDENTE || destino == FALHA_ENVIO;
			case CONFIRMADA, REJEITADA -> false;
		};
	}

	/** O destino, se a transição for permitida; senão, {@link IllegalStateException}. */
	StatusOperacao transicionarPara(StatusOperacao destino) {
		if (!podeIrPara(destino)) {
			throw new IllegalStateException("Transição de estado inválida: " + this + " → " + destino);
		}
		return destino;
	}

}
