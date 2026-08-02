package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaTransacao(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long contaId,
        String nomeConta,
        Categoria categoria,
        Long cartaoId,
        String nomeCartao,
        LocalDate dataTransacao
) {
}
