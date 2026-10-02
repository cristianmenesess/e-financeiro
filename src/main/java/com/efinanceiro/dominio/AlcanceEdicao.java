package com.efinanceiro.dominio;

/**
 * Quais lançamentos uma edição de recorrência ou assinatura refaz.
 */
public enum AlcanceEdicao {
    /** Só os que ainda não aconteceram; os passados ficam como estão. */
    FUTURAS,
    /** Todos, inclusive os passados — usado pra corrigir um cadastro errado. */
    TODAS
}
