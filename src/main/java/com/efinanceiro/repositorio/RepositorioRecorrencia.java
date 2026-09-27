package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Recorrencia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositorioRecorrencia extends JpaRepository<Recorrencia, Long> {

    /**
     * Lista todas as recorrências pertencentes a um usuário, já trazendo conta e cartão na mesma
     * consulta (a resposta usa o nome dos dois).
     *
     * @param usuarioId Id do usuário dono das recorrências
     * @return Lista de recorrências do usuário
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    List<Recorrencia> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma recorrência pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da recorrência
     * @param usuarioId Id do usuário dono da recorrência
     * @return Recorrência encontrada, se existir e pertencer ao usuário
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    Optional<Recorrencia> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Apaga todas as recorrências vinculadas a uma conta, num único DELETE — usado ao excluir a
     * conta em cascata, pra não deixar uma FK pra uma conta que não existe mais.
     *
     * @param contaId Id da conta cujas recorrências serão apagadas
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Recorrencia r where r.conta.id = :contaId")
    void deleteByContaId(@Param("contaId") Long contaId);

    /**
     * Apaga todas as recorrências de um usuário, num único DELETE — usado na exclusão do cadastro
     * (depois das transações, que apontam pra elas).
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Recorrencia r where r.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Move todas as recorrências de uma categoria pra outra, num único UPDATE — usado ao excluir uma
     * categoria personalizada.
     *
     * @param origem Categoria que vai ser excluída
     * @param destino Categoria fixa que recebe as recorrências
     * @return Quantidade de recorrências movidas
     */
    @Modifying(flushAutomatically = true)
    @Query("update Recorrencia r set r.categoria = :destino where r.categoria = :origem")
    int moverCategoria(@Param("origem") Categoria origem, @Param("destino") Categoria destino);
}
