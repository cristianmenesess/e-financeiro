package com.efinanceiro.repositorio.projecao;

import com.efinanceiro.dominio.TipoTransacao;

import java.math.BigDecimal;

/**
 * Soma dos valores das transações de um tipo (entrada ou saída), calculada no banco.
 */
public record TotalPorTipo(TipoTransacao tipo, BigDecimal total) {
}
