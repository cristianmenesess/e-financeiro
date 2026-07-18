package com.efinanceiro.dto.resposta;

import java.math.BigDecimal;

public record RespostaResumoSaldo(
        BigDecimal saldo,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas
) {
}
