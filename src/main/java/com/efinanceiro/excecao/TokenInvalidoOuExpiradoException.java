package com.efinanceiro.excecao;

public class TokenInvalidoOuExpiradoException extends RuntimeException {

    public TokenInvalidoOuExpiradoException(String mensagem) {
        super(mensagem);
    }
}
