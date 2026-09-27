package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V8 mexe em dado de produção (converte o texto da categoria em FK e apaga a coluna texto).
 * Aqui o Flyway roda num schema separado até a V7, recebe dados no formato antigo e só então
 * aplica a V8 — igual vai acontecer no banco de produção.
 */
class MigracaoCategoriasTeste extends TesteIntegracao {

    private static final String SCHEMA = "teste_migracao";

    @Autowired
    private DataSource dataSource;

    private Flyway flywayAte(String versao) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(versao)
                .load();
    }

    @Test
    void v8ConverteOTextoAntigoParaACategoriaFixaCerta() {
        try {
            flywayAte("7").migrate();

            jdbcTemplate.update("insert into " + SCHEMA + ".usuarios (nome, email, senha_hash) values ('Antigo', 'antigo@teste.com', 'x')");
            Long usuarioId = jdbcTemplate.queryForObject("select id from " + SCHEMA + ".usuarios", Long.class);
            jdbcTemplate.update("insert into " + SCHEMA + ".contas (usuario_id, nome, cor_fundo, cor_texto) values (?, 'Pessoal', '#FFFFFF', '#000000')", usuarioId);
            Long contaId = jdbcTemplate.queryForObject("select id from " + SCHEMA + ".contas", Long.class);

            for (String codigo : List.of("RENDA", "DESPESA", "ALIMENTACAO", "MORADIA", "OUTRO")) {
                jdbcTemplate.update("insert into " + SCHEMA + ".transacoes (usuario_id, descricao, valor, tipo, categoria, conta_id, data_transacao) "
                        + "values (?, ?, 10, 'SAIDA', ?, ?, current_date)", usuarioId, codigo, codigo, contaId);
            }

            // Valor fora do enum antigo (não deveria existir, mas a V8 não pode quebrar por causa dele)
            jdbcTemplate.update("insert into " + SCHEMA + ".transacoes (usuario_id, descricao, valor, tipo, categoria, conta_id, data_transacao) "
                    + "values (?, 'LEGADO', 10, 'SAIDA', 'LEGADO', ?, current_date)", usuarioId, contaId);

            jdbcTemplate.update("insert into " + SCHEMA + ".recorrencias (usuario_id, conta_id, descricao, valor, tipo, categoria, total_parcelas, data_inicio) "
                    + "values (?, ?, 'Aluguel', 100, 'SAIDA', 'MORADIA', 3, current_date)", usuarioId, contaId);

            flywayAte("8").migrate();

            List<Map<String, Object>> transacoes = jdbcTemplate.queryForList(
                    "select t.descricao, c.codigo from " + SCHEMA + ".transacoes t join " + SCHEMA + ".categorias c on c.id = t.categoria_id");
            assertThat(transacoes).hasSize(6);
            transacoes.forEach(linha -> assertThat(linha.get("codigo"))
                    .isEqualTo("LEGADO".equals(linha.get("descricao")) ? "OUTRO" : linha.get("descricao")));

            assertThat(jdbcTemplate.queryForObject("select c.codigo from " + SCHEMA + ".recorrencias r join " + SCHEMA
                    + ".categorias c on c.id = r.categoria_id", String.class)).isEqualTo("MORADIA");

            assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema = ? "
                    + "and table_name in ('transacoes', 'recorrencias') and column_name = 'categoria'", Integer.class, SCHEMA)).isZero();

            assertThat(jdbcTemplate.queryForList("select codigo from " + SCHEMA + ".categorias where usuario_id is null order by id", String.class))
                    .containsExactly("RENDA", "DESPESA", "ALIMENTACAO", "MORADIA", "OUTRO");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }
}
