package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Assinatura;
import com.efinanceiro.dominio.Categoria;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositorioAssinatura extends JpaRepository<Assinatura, Long> {

    /**
     * Lista as assinaturas de um usuário, já trazendo conta, cartão e categoria (a resposta usa o
     * nome dos três).
     *
     * @param usuarioId Id do usuário dono das assinaturas
     * @return Assinaturas do usuário
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    List<Assinatura> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma assinatura pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da assinatura
     * @param usuarioId Id do usuário dono
     * @return Assinatura encontrada, se existir e pertencer ao usuário
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    Optional<Assinatura> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Lista as assinaturas ativas de todos os usuários — usado pra lançar as próximas cobranças.
     *
     * @return Assinaturas não canceladas
     */
    @EntityGraph(attributePaths = {"usuario", "conta", "cartao", "categoria"})
    List<Assinatura> findByCanceladaEmIsNull();

    /**
     * Lista as assinaturas ativas de um usuário — usado pra lançar as próximas cobranças no login.
     *
     * @param usuarioId Id do usuário
     * @return Assinaturas não canceladas do usuário
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    List<Assinatura> findByUsuarioIdAndCanceladaEmIsNull(Long usuarioId);

    /**
     * Apaga todas as assinaturas de uma conta num único DELETE — usado ao excluir a conta em
     * cascata (depois das transações, que apontam pra elas).
     *
     * @param contaId Id da conta
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Assinatura a where a.conta.id = :contaId")
    void deleteByContaId(@Param("contaId") Long contaId);

    /**
     * Apaga todas as assinaturas de um usuário num único DELETE — usado na exclusão do cadastro.
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Assinatura a where a.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Move as assinaturas de uma categoria pra outra num único UPDATE — usado ao excluir uma
     * categoria personalizada.
     *
     * @param origem Categoria que vai ser excluída
     * @param destino Categoria fixa que recebe as assinaturas
     * @return Quantidade de assinaturas movidas
     */
    @Modifying(flushAutomatically = true)
    @Query("update Assinatura a set a.categoria = :destino where a.categoria = :origem")
    int moverCategoria(@Param("origem") Categoria origem, @Param("destino") Categoria destino);
}
