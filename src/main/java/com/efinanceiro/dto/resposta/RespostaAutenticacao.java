package com.efinanceiro.dto.resposta;

public record RespostaAutenticacao(
        String token,
        String nome,
        String email
) {
}
