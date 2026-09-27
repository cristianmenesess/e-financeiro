package com.efinanceiro.excecao;

public class LimiteDeRequisicoesExcedidoException extends RuntimeException {

    public LimiteDeRequisicoesExcedidoException(String mensagem) {
        super(mensagem);
    }
}
