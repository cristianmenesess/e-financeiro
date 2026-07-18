package com.efinanceiro.dto.resposta;

import java.math.BigDecimal;

public record RespostaCartao(
        Long id,
        String nome,
        String corFundo,
        String corTexto,
        BigDecimal gastoNoMes
) {
}
