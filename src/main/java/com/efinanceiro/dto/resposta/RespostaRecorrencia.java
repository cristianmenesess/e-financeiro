package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaRecorrencia(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Categoria categoria,
        Long contaId,
        String nomeConta,
        Long cartaoId,
        String nomeCartao,
        Integer totalParcelas,
        Integer parcelasRestantes,
        LocalDate dataInicio
) {
}
