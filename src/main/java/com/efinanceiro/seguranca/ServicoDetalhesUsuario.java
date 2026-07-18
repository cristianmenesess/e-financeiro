package com.efinanceiro.seguranca;

import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class ServicoDetalhesUsuario implements UserDetailsService {

    private final RepositorioUsuario repositorioUsuario;

    public ServicoDetalhesUsuario(RepositorioUsuario repositorioUsuario) {
        this.repositorioUsuario = repositorioUsuario;
    }

    /**
     * Carrega os detalhes de autenticação de um usuário a partir do e-mail.
     *
     * @param email E-mail usado como identificador de login
     * @return Detalhes do usuário para o Spring Security
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));

        return new User(usuario.getEmail(), usuario.getSenhaHash(), Collections.emptyList());
    }
}
