package com.efinanceiro.dto.requisicao;

/**
 * Fechamento e vencimento de um cartão que a importação vai criar.
 */
public record RequisicaoCartaoImportado(
        String nome,
        Integer diaFechamento,
        Integer diaVencimento
)
{}
