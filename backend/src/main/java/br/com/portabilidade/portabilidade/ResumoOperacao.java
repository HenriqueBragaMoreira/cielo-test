package br.com.portabilidade.portabilidade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Uma linha do histórico de operações (ADR-0013): o total e a quantidade saem das parcelas gravadas. */
record ResumoOperacao(UUID id, String beneficiarioId, StatusOperacao status, long quantidadeParcelas,
		BigDecimal valorTotal, Instant criadaEm, Instant atualizadaEm) {
}
