package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.Periodicidade;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaAssinatura(
        Long id,
        String descricao,
        BigDecimal valor,
        Periodicidade periodicidade,
        Long categoriaId,
        String nomeCategoria,
        Long contaId,
        String nomeConta,
        Long cartaoId,
        String nomeCartao,
        LocalDate dataInicio,
        LocalDate proximaCobranca,
        LocalDate canceladaEm
) {
}
