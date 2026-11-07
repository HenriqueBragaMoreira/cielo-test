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
	private final TravaDeParcelas trava;

	AgendaService(AutorizacaoService autorizacoes, CentralClient central, TravaDeParcelas trava) {
		this.autorizacoes = autorizacoes;
		this.central = central;
		this.trava = trava;
	}

	/**
	 * A agenda da Central, com a elegibilidade de cada parcela, trava de parcela incluída; só para beneficiários com
	 * autorização registrada.
	 */
	List<ParcelaAgenda> consultar(String beneficiarioId) {
		autorizacoes.exigirAutorizacao(beneficiarioId);
		var parcelas = central.consultarAgenda(beneficiarioId);
		var travadas = trava.travadas(parcelas.stream().map(CentralClient.Parcela::parcelaId).toList());
		return parcelas.stream()
				.map(parcela -> ParcelaAgenda.de(parcela, travadas.contains(parcela.parcelaId())))
				.toList();
	}

	record ParcelaAgenda(String parcelaId, String convenioId, BigDecimal valor, LocalDate dataDesconto, String status,
			boolean elegivel, String motivoInelegibilidade) {

		static ParcelaAgenda de(CentralClient.Parcela parcela, boolean travada) {
			var elegibilidade = Elegibilidade.de(parcela, travada);
			return new ParcelaAgenda(parcela.parcelaId(), parcela.convenioId(), parcela.valor(), parcela.dataDesconto(),
					parcela.status(), elegibilidade.elegivel(), elegibilidade.motivoInelegibilidade());
		}

	}

}
