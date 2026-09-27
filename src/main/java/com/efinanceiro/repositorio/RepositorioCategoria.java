package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositorioCategoria extends JpaRepository<Categoria, Long> {

    /**
     * Lista as categorias que o usuário enxerga: as fixas do sistema e as dele. Left join porque
     * as fixas não têm usuário (um join comum as deixaria de fora).
     *
     * @param usuarioId Id do usuário
     * @return Fixas + personalizadas do usuário, sem ordem definida
     */
    @Query("select c from Categoria c left join c.usuario u where u is null or u.id = :usuarioId")
    List<Categoria> listarVisiveisAoUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Busca uma categoria que o usuário pode usar num lançamento (fixa ou dele).
     *
     * @param id Id da categoria
     * @param usuarioId Id do usuário
     * @return Categoria, se existir e for fixa ou do usuário
     */
    @Query("select c from Categoria c left join c.usuario u where c.id = :id and (u is null or u.id = :usuarioId)")
    Optional<Categoria> buscarVisivelAoUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Busca uma categoria personalizada do usuário (fixas nunca voltam aqui) — usado pra editar e excluir.
     *
     * @param id Id da categoria
     * @param usuarioId Id do usuário dono
     * @return Categoria personalizada do usuário, se existir
     */
    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Busca uma categoria fixa pelo código (ex: OUTRO, RENDA).
     *
     * @param codigo Código da categoria fixa
     * @return Categoria fixa, se existir
     */
    Optional<Categoria> findByCodigo(String codigo);

    /**
     * Conta as categorias personalizadas do usuário — usado no limite por usuário.
     *
     * @param usuarioId Id do usuário
     * @return Quantidade de categorias personalizadas
     */
    long countByUsuarioId(Long usuarioId);

    /**
     * Verifica se já existe, entre as fixas e as do usuário, outra categoria com o mesmo nome
     * (ignorando maiúsculas/minúsculas).
     *
     * @param nome Nome a verificar (já sem espaços nas pontas)
     * @param usuarioId Id do usuário
     * @param idIgnorado Id da própria categoria numa edição (0 na criação)
     * @return true se o nome já estiver em uso
     */
    @Query("""
            select count(c) > 0 from Categoria c left join c.usuario u
            where lower(c.nome) = lower(:nome) and (u is null or u.id = :usuarioId) and c.id <> :idIgnorado
            """)
    boolean existeNomeVisivel(@Param("nome") String nome, @Param("usuarioId") Long usuarioId, @Param("idIgnorado") Long idIgnorado);

    /**
     * Apaga todas as categorias personalizadas de um usuário, num único DELETE — usado na exclusão
     * do cadastro (as fixas não têm usuário e nunca são apagadas).
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Categoria c where c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
