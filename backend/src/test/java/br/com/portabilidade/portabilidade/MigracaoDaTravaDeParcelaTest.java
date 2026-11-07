package br.com.portabilidade.portabilidade;

import br.com.portabilidade.ApiTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V3 cria a trava de parcela sobre operações que já existiam: cada parcela fica travada só se a operação dela estiver
 * ativa. Roda as migrações num schema à parte, para gravar dados no meio delas.
 */
class MigracaoDaTravaDeParcelaTest extends ApiTest {

	private static final String SCHEMA = "migracao_trava_de_parcela";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbc;

	@AfterEach
	void descartarSchema() {
		jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void parcelasDeOperacoesGravadasAntesDaTravaSoFicamTravadasSeAOperacaoEstiverAtiva() {
		migrarAte(MigrationVersion.fromVersion("2"));
		// as operações que liberaram P001 não podem impedir a trava da que está com ela
		var rejeitada = gravarOperacao("REJEITADA", "P001");
		var falhaEnvio = gravarOperacao("FALHA_ENVIO", "P001");
		var pendente = gravarOperacao("PENDENTE", "P001");
		var confirmada = gravarOperacao("CONFIRMADA", "P002");

		migrarAte(MigrationVersion.LATEST);

		var ativas = jdbc.sql("select operacao_id, ativa from " + SCHEMA + ".operacao_parcela")
				.query((linha, i) -> Map.entry(linha.getObject("operacao_id", UUID.class), linha.getBoolean("ativa")))
				.list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		assertThat(ativas).containsExactlyInAnyOrderEntriesOf(Map.of(
				rejeitada, false, falhaEnvio, false, pendente, true, confirmada, true));
	}

	private void migrarAte(MigrationVersion versao) {
		Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(versao).load().migrate();
	}

	private UUID gravarOperacao(String status, String parcelaId) {
		var id = UUID.randomUUID();
		jdbc.sql("insert into " + SCHEMA + ".operacao_portabilidade (id, beneficiario_id, status, banco, agencia, conta,"
				+ " digito, chave_idempotencia, hash_corpo, criada_em, atualizada_em)"
				+ " values (?, 'BEN001', ?, '001', '1234', '56789', '0', ?, 'hash', now(), now())")
				.params(id, status, UUID.randomUUID())
				.update();
		jdbc.sql("insert into " + SCHEMA + ".operacao_parcela (operacao_id, parcela_id, valor) values (?, ?, 100)")
				.params(id, parcelaId)
				.update();
		return id;
	}

}
