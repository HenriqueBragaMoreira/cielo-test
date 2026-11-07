package br.com.portabilidade.portabilidade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static br.com.portabilidade.portabilidade.StatusOperacao.CONFIRMADA;
import static br.com.portabilidade.portabilidade.StatusOperacao.FALHA_ENVIO;
import static br.com.portabilidade.portabilidade.StatusOperacao.PENDENTE;
import static br.com.portabilidade.portabilidade.StatusOperacao.REJEITADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Máquina de estados da operação (ADR-0007). */
class StatusOperacaoTest {

	@Test
	void operacaoNascePendenteOuEmFalhaDeEnvio() {
		assertThat(Arrays.stream(StatusOperacao.values()).filter(StatusOperacao::podeSerInicial))
				.containsExactlyInAnyOrder(PENDENTE, FALHA_ENVIO);
	}

	@ParameterizedTest(name = "{0} → {1}: {2}")
	@CsvSource({
			"PENDENTE,    CONFIRMADA,  true",
			"PENDENTE,    REJEITADA,   true",
			"PENDENTE,    PENDENTE,    false",
			"PENDENTE,    FALHA_ENVIO, false",
			"FALHA_ENVIO, PENDENTE,    true",
			"FALHA_ENVIO, FALHA_ENVIO, true",
			"FALHA_ENVIO, CONFIRMADA,  false",
			"FALHA_ENVIO, REJEITADA,   false",
			"CONFIRMADA,  PENDENTE,    false",
			"CONFIRMADA,  CONFIRMADA,  false",
			"CONFIRMADA,  REJEITADA,   false",
			"CONFIRMADA,  FALHA_ENVIO, false",
			"REJEITADA,   PENDENTE,    false",
			"REJEITADA,   CONFIRMADA,  false",
			"REJEITADA,   REJEITADA,   false",
			"REJEITADA,   FALHA_ENVIO, false",
	})
	void transicoesPermitidas(StatusOperacao origem, StatusOperacao destino, boolean permitida) {
		assertThat(origem.podeIrPara(destino)).isEqualTo(permitida);
	}

	@Test
	void transicaoPermitidaLevaAoDestino() {
		assertThat(PENDENTE.transicionarPara(CONFIRMADA)).isEqualTo(CONFIRMADA);
		assertThat(FALHA_ENVIO.transicionarPara(PENDENTE)).isEqualTo(PENDENTE);
	}

	@Test
	void transicaoProibidaEhRecusada() {
		assertThatIllegalStateException().isThrownBy(() -> CONFIRMADA.transicionarPara(REJEITADA))
				.withMessage("Transição de estado inválida: CONFIRMADA → REJEITADA");
	}

}
