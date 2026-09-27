package com.efinanceiro.dto.resposta;

/**
 * Resultado da exclusão de uma categoria: quantas transações + recorrências foram movidas pra
 * categoria fixa genérica do mesmo tipo.
 */
public record RespostaExclusaoCategoria(long movidas) {
}
