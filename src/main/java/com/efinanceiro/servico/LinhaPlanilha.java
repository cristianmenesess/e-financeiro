package com.efinanceiro.servico;

import java.util.Map;

/**
 * Uma linha de dados da planilha: número da linha no arquivo e o texto de cada coluna do modelo.
 */
public record LinhaPlanilha(int numero, Map<String, String> campos) {

    /**
     * Texto de uma coluna, sem espaços nas pontas (vazio se a coluna não existir no arquivo).
     *
     * @param coluna Coluna (constante de ColunasPlanilha)
     * @return Texto da célula
     */
    public String campo(String coluna) {
        return campos.getOrDefault(coluna, "").trim();
    }
}
