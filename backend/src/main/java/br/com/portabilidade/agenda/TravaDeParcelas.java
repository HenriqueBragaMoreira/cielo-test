package br.com.portabilidade.agenda;

import java.util.Collection;
import java.util.Set;

/**
 * As parcelas que já estão numa operação de portabilidade {@code PENDENTE} ou {@code CONFIRMADA} (ADR-0005). Quem a
 * implementa é o módulo {@code portabilidade}, dono das operações; a agenda só a consulta.
 */
public interface TravaDeParcelas {

	/** Das parcelas dadas, as travadas numa operação ativa. */
	Set<String> travadas(Collection<String> parcelaIds);

}
