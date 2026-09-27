package com.efinanceiro.repositorio.projecao;

import java.math.BigDecimal;

/**
 * Soma das saídas de um cartão num período, calculada no banco.
 */
public record GastoPorCartao(Long cartaoId, BigDecimal total) {
}
