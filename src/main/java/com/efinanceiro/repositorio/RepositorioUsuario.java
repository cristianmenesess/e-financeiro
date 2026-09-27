package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RepositorioUsuario extends JpaRepository<Usuario, Long> {

    /**
     * Busca um usuário pelo e-mail exato, como gravado no banco — usado pra carregar o usuário
     * a partir do token JWT, que guarda o e-mail já no formato gravado.
     *
     * @param email E-mail do usuário
     * @return Usuário encontrado, se existir
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Busca um usuário pelo e-mail ignorando maiúsculas/minúsculas — usado no login e no
     * "esqueci a senha", que recebem o e-mail digitado. Em caso de contas antigas duplicadas só
     * por maiúsculas (criadas antes da normalização de e-mail), fica com a mais antiga.
     *
     * @param email E-mail digitado pelo usuário
     * @return Usuário encontrado, se existir
     */
    Optional<Usuario> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    /**
     * Verifica se já existe um usuário cadastrado com o e-mail informado, ignorando
     * maiúsculas/minúsculas.
     *
     * @param email E-mail a verificar
     * @return true se já existir um usuário com esse e-mail
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Busca um usuário pelo hash do token de redefinição de senha.
     *
     * @param tokenRedefinicaoHash Hash SHA-256 do token de redefinição
     * @return Usuário encontrado, se o hash corresponder a um token ativo
     */
    Optional<Usuario> findByTokenRedefinicaoHash(String tokenRedefinicaoHash);

    /**
     * Apaga o usuário num DELETE direto — usado por último na exclusão do cadastro, depois de
     * todos os dados que apontam pra ele.
     *
     * @param id Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Usuario u where u.id = :id")
    void apagarPorId(@Param("id") Long id);
}
