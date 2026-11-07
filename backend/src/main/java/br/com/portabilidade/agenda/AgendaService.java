package br.com.portabilidade.agenda;

import br.com.portabilidade.autorizacao.AutorizacaoService;
import br.com.portabilidade.central.CentralClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
class AgendaService {

	private final AutorizacaoService autorizacoes;
	private final CentralClient central;

	AgendaService(AutorizacaoService autorizacoes, CentralClient central) {
		this.autorizacoes = autorizacoes;
		this.central = central;
	}

	/** A agenda da Central, com a elegibilidade de cada parcela; só para beneficiários com autorização registrada. */
	List<ParcelaAgenda> consultar(String beneficiarioId) {
		autorizacoes.exigirAutorizacao(beneficiarioId);
		return central.consultarAgenda(beneficiarioId).stream()
				.map(ParcelaAgenda::de)
				.toList();
	}

	record ParcelaAgenda(String parcelaId, String convenioId, BigDecimal valor, LocalDate dataDesconto, String status,
			boolean elegivel, String motivoInelegibilidade) {

		static ParcelaAgenda de(CentralClient.Parcela parcela) {
			var elegibilidade = Elegibilidade.de(parcela);
			return new ParcelaAgenda(parcela.parcelaId(), parcela.convenioId(), parcela.valor(), parcela.dataDesconto(),
					parcela.status(), elegibilidade.elegivel(), elegibilidade.motivoInelegibilidade());
		}

	}

}
