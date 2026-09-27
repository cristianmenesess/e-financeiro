package com.efinanceiro.excecao;

public class ServicoExternoIndisponivelException extends RuntimeException {

    public ServicoExternoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
