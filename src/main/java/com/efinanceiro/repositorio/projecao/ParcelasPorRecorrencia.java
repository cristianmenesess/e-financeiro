package com.efinanceiro.repositorio.projecao;

/**
 * Quantidade de parcelas de uma recorrência que atendem a um filtro (ex: parcelas futuras),
 * calculada no banco.
 */
public record ParcelasPorRecorrencia(Long recorrenciaId, Long quantidade) {
}
