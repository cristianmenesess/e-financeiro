package com.efinanceiro.dto.resposta;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaCartao(
        Long id,
        String nome,
        String corFundo,
        String corTexto,
        Integer diaFechamento,
        Integer diaVencimento,
        BigDecimal faturaAtual,
        LocalDate vencimentoFaturaAtual
) {
}
