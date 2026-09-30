package com.efinanceiro.servico;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Colunas do modelo de planilha de movimentações (importação e exportação).
 */
public final class ColunasPlanilha {

    public static final String DATA = "data";
    public static final String DESCRICAO = "descrição";
    public static final String TIPO = "tipo";
    public static final String VALOR = "valor";
    public static final String CATEGORIA = "categoria";
    public static final String CONTA = "conta";
    public static final String CARTAO = "cartão";
    public static final String PARCELA_ATUAL = "parcela atual";
    public static final String TOTAL_PARCELAS = "total de parcelas";

    /** Ordem das colunas no modelo e na exportação. */
    public static final List<String> ORDEM = List.of(DATA, DESCRICAO, TIPO, VALOR, CATEGORIA, CONTA, CARTAO, PARCELA_ATUAL, TOTAL_PARCELAS);

    public static final List<String> OBRIGATORIAS = List.of(DATA, DESCRICAO, TIPO, VALOR);

    // Nome normalizado (sem acento, minúsculo) -> coluna
    private static final Map<String, String> APELIDOS = Map.of(
            "data", DATA,
            "descricao", DESCRICAO,
            "tipo", TIPO,
            "valor", VALOR,
            "categoria", CATEGORIA,
            "conta", CONTA,
            "cartao", CARTAO,
            "parcela atual", PARCELA_ATUAL,
            "total de parcelas", TOTAL_PARCELAS,
            "total parcelas", TOTAL_PARCELAS
    );

    private ColunasPlanilha() {
    }

    /**
     * Identifica a coluna a partir do texto do cabeçalho, ignorando maiúsculas, acentos e espaços extras.
     *
     * @param cabecalho Texto da célula do cabeçalho
     * @return Nome da coluna, ou null se não for uma coluna do modelo
     */
    public static String identificar(String cabecalho) {
        return APELIDOS.get(normalizar(cabecalho));
    }

    /**
     * Remove acentos, espaços extras e deixa minúsculo — usado pra comparar nomes.
     *
     * @param texto Texto original
     * @return Texto normalizado
     */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
