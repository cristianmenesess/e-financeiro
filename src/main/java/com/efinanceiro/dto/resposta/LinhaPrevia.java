package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha válida da planilha, como vai ser importada (nomes já resolvidos).
 */
public record LinhaPrevia(
        int linha,
        LocalDate data,
        String descricao,
        TipoTransacao tipo,
        BigDecimal valor,
        String categoria,
        String conta,
        String cartao,
        Integer parcelaAtual,
        Integer totalParcelas,
        boolean possivelDuplicado
) {
}
