package com.efinanceiro.servico;

import java.util.Locale;

/**
 * Regra única de normalização de e-mail (cadastro, login, esqueci a senha, troca de e-mail no
 * perfil): sem espaços nas pontas e minúsculo.
 */
public final class NormalizadorEmail {

    private NormalizadorEmail() {
    }

    /**
     * Normaliza um e-mail digitado pelo usuário.
     *
     * @param email E-mail como veio na requisição
     * @return E-mail sem espaços nas pontas e em minúsculas
     */
    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
