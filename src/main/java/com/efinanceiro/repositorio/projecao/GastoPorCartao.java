package com.efinanceiro.repositorio.projecao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Soma das saídas de um cartão num dia, calculada no banco.
 */
public record GastoPorCartao(Long cartaoId, LocalDate data, BigDecimal total) {
}
