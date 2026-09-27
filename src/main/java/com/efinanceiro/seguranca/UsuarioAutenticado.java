package com.efinanceiro.seguranca;

import lombok.Getter;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Collections;

/**
 * Usuário logado guardado no contexto de segurança. Além do e-mail/senha que o Spring Security
 * já conhece, carrega o id (evita buscar o usuário de novo no banco em cada serviço) e o momento
 * da última troca de senha (usado pra invalidar tokens emitidos antes dela).
 */
@Getter
public class UsuarioAutenticado extends User {

    private final Long id;
    private final Instant senhaAlteradaEm;

    public UsuarioAutenticado(Long id, String email, String senhaHash, Instant senhaAlteradaEm) {
        super(email, senhaHash, Collections.emptyList());
        this.id = id;
        this.senhaAlteradaEm = senhaAlteradaEm;
    }
}
