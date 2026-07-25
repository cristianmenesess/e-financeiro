package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositorioUsuario extends JpaRepository<Usuario, Long> {

    /**
     * Busca um usuário pelo e-mail.
     *
     * @param email E-mail do usuário
     * @return Usuário encontrado, se existir
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Verifica se já existe um usuário cadastrado com o e-mail informado.
     *
     * @param email E-mail a verificar
     * @return true se já existir um usuário com esse e-mail
     */
    boolean existsByEmail(String email);

    /**
     * Busca um usuário pelo hash do token de redefinição de senha.
     *
     * @param tokenRedefinicaoHash Hash SHA-256 do token de redefinição
     * @return Usuário encontrado, se o hash corresponder a um token ativo
     */
    Optional<Usuario> findByTokenRedefinicaoHash(String tokenRedefinicaoHash);
}
