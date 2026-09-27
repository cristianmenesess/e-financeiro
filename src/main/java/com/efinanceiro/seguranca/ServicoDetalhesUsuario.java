package com.efinanceiro.seguranca;

import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ServicoDetalhesUsuario implements UserDetailsService {

    private final RepositorioUsuario repositorioUsuario;

    public ServicoDetalhesUsuario(RepositorioUsuario repositorioUsuario) {
        this.repositorioUsuario = repositorioUsuario;
    }

    /**
     * Carrega os detalhes de autenticação de um usuário a partir do e-mail gravado no token.
     *
     * @param email E-mail usado como identificador de login
     * @return Usuário autenticado (id, e-mail, hash da senha e data da última troca de senha)
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário do token não existe mais"));

        return new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), usuario.getSenhaHash(), usuario.getSenhaAlteradaEm());
    }
}
