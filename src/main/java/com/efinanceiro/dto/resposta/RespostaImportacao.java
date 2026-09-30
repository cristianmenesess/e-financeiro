package com.efinanceiro.dto.resposta;

public record RespostaImportacao(
        int lancamentosAvulsos,
        int recorrencias,
        int parcelasGeradas,
        int categoriasCriadas,
        int contasCriadas,
        int cartoesCriados,
        int duplicadosPulados
) {
}
