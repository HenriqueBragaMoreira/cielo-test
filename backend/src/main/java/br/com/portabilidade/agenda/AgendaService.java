package br.com.portabilidade.agenda;

import br.com.portabilidade.autorizacao.AutorizacaoRepository;
import br.com.portabilidade.central.CentralClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
class AgendaService {

	private final AutorizacaoRepository autorizacoes;
	private final CentralClient central;

	AgendaService(AutorizacaoRepository autorizacoes, CentralClient central) {
		this.autorizacoes = autorizacoes;
		this.central = central;
	}

	/** A agenda da Central, com a elegibilidade de cada parcela; só para beneficiários com autorização registrada. */
	List<ParcelaAgenda> consultar(String beneficiarioId) {
		if (!autorizacoes.existsById(beneficiarioId)) {
			throw new BeneficiarioSemAutorizacaoException(beneficiarioId);
		}
		return central.consultarAgenda(beneficiarioId).stream()
				.map(parcela -> {
					var motivo = motivoInelegibilidade(parcela);
					return new ParcelaAgenda(parcela.parcelaId(), parcela.convenioId(), parcela.valor(), parcela.dataDesconto(),
							parcela.status(), motivo == null, motivo);
				})
				.toList();
	}

	/**
	 * Regras de elegibilidade (ADR-0006); {@code null} quando a parcela é elegível.
	 * O PRD de portabilidade acrescenta aqui "não está numa operação PENDENTE ou CONFIRMADA".
	 */
	private static String motivoInelegibilidade(CentralClient.Parcela parcela) {
		if (!"ABERTA".equals(parcela.status())) {
			return "A parcela está com status " + parcela.status() + "; só parcelas ABERTA podem ser portadas.";
		}
		return null;
	}

	record ParcelaAgenda(String parcelaId, String convenioId, BigDecimal valor, LocalDate dataDesconto, String status,
			boolean elegivel, String motivoInelegibilidade) {
	}

}
