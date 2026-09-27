package com.efinanceiro.dto.resposta;

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
        Long categoriaId,
        String nomeCategoria,
        Long cartaoId,
        String nomeCartao,
        LocalDate dataTransacao,
        Long recorrenciaId,
        Integer numeroParcela,
        Integer totalParcelas
) {
}
