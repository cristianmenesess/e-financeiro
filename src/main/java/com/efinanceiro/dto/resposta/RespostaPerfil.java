package com.efinanceiro.dto.resposta;

/**
 * Dados do perfil do usuário logado. O token só vem preenchido quando um token novo foi emitido
 * (troca de e-mail ou de senha) — o front deve trocar o da sessão por ele.
 */
public record RespostaPerfil(
        String nome,
        String email,
        String fotoUrl,
        String token
) {
}
