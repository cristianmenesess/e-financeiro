package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.repositorio.projecao.GastoPorCartao;
import com.efinanceiro.repositorio.projecao.ParcelasPorRecorrencia;
import com.efinanceiro.repositorio.projecao.TotalPorTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepositorioTransacao extends JpaRepository<Transacao, Long> {

    /**
     * Lista as transações de um usuário, já trazendo conta, cartão e recorrência na mesma consulta
     * (sem isso, montar a resposta dispara uma consulta extra por transação). A ordenação e a
     * paginação vêm do Pageable — Pageable.unpaged(ordenacao) devolve a lista inteira.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param paginacao Página, tamanho e ordenação
     * @return Página de transações
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "recorrencia", "categoria"})
    Page<Transacao> findByUsuarioId(Long usuarioId, Pageable paginacao);

    /**
     * Lista as transações de um usuário filtradas por conta — mesma finalidade da variante acima.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param contaId Id da conta para filtrar
     * @param paginacao Página, tamanho e ordenação
     * @return Página de transações da conta
     */
    @EntityGraph(attributePaths = {"conta", "cartao", "recorrencia", "categoria"})
    Page<Transacao> findByUsuarioIdAndContaId(Long usuarioId, Long contaId, Pageable paginacao);

    /**
     * Soma entradas e saídas já ocorridas (até a data informada) de um usuário, direto no banco —
     * usado no saldo, que não deve contar parcelas futuras de recorrências.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param data Data de referência (normalmente hoje) — inclui transações com data <= essa
     * @return Um total por tipo que tiver transações (tipo sem transação não aparece)
     */
    @Query("""
            select new com.efinanceiro.repositorio.projecao.TotalPorTipo(t.tipo, sum(t.valor))
            from Transacao t
            where t.usuario.id = :usuarioId and t.dataTransacao <= :data
            group by t.tipo
            """)
    List<TotalPorTipo> somarPorTipoAte(@Param("usuarioId") Long usuarioId, @Param("data") LocalDate data);

    /**
     * Soma entradas e saídas já ocorridas de um usuário numa conta — mesma finalidade da variante
     * acima, para quando o saldo é filtrado por conta.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param contaId Id da conta para filtrar
     * @param data Data de referência (normalmente hoje) — inclui transações com data <= essa
     * @return Um total por tipo que tiver transações na conta
     */
    @Query("""
            select new com.efinanceiro.repositorio.projecao.TotalPorTipo(t.tipo, sum(t.valor))
            from Transacao t
            where t.usuario.id = :usuarioId and t.conta.id = :contaId and t.dataTransacao <= :data
            group by t.tipo
            """)
    List<TotalPorTipo> somarPorTipoNaContaAte(@Param("usuarioId") Long usuarioId,
                                              @Param("contaId") Long contaId,
                                              @Param("data") LocalDate data);

    /**
     * Busca uma transação pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da transação
     * @param usuarioId Id do usuário dono da transação
     * @return Transação encontrada, se existir e pertencer ao usuário
     */
    Optional<Transacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Soma, numa consulta só, o gasto de cada cartão de um usuário num intervalo de datas — usado
     * pra listar os cartões com o gasto do mês sem uma consulta por cartão.
     *
     * @param usuarioId Id do usuário dono dos cartões
     * @param tipo Tipo da transação (sempre SAIDA nesse uso)
     * @param inicio Data inicial do intervalo (inclusive)
     * @param fim Data final do intervalo (inclusive)
     * @return Um total por cartão que tiver gastos no período (cartão sem gasto não aparece)
     */
    @Query("""
            select new com.efinanceiro.repositorio.projecao.GastoPorCartao(t.cartao.id, sum(t.valor))
            from Transacao t
            where t.usuario.id = :usuarioId and t.cartao is not null and t.tipo = :tipo
              and t.dataTransacao between :inicio and :fim
            group by t.cartao.id
            """)
    List<GastoPorCartao> somarPorCartao(@Param("usuarioId") Long usuarioId,
                                        @Param("tipo") TipoTransacao tipo,
                                        @Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    /**
     * Soma o gasto de um único cartão num intervalo de datas — usado ao criar/editar um cartão.
     *
     * @param cartaoId Id do cartão
     * @param tipo Tipo da transação (sempre SAIDA nesse uso)
     * @param inicio Data inicial do intervalo (inclusive)
     * @param fim Data final do intervalo (inclusive)
     * @return Total gasto no período (zero se não houver transações)
     */
    @Query("""
            select coalesce(sum(t.valor), 0)
            from Transacao t
            where t.cartao.id = :cartaoId and t.tipo = :tipo and t.dataTransacao between :inicio and :fim
            """)
    BigDecimal somarDoCartao(@Param("cartaoId") Long cartaoId,
                             @Param("tipo") TipoTransacao tipo,
                             @Param("inicio") LocalDate inicio,
                             @Param("fim") LocalDate fim);

    /**
     * Apaga todas as transações vinculadas a uma conta, num único DELETE — usado ao excluir a conta
     * em cascata (o delete derivado do Spring Data carregaria e apagaria uma transação por vez).
     *
     * @param contaId Id da conta cujas transações serão apagadas
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Transacao t where t.conta.id = :contaId")
    void deleteByContaId(@Param("contaId") Long contaId);

    /**
     * Conta quantas transações de uma recorrência ainda estão no futuro (depois da data
     * informada) — usado pra calcular "parcelas restantes".
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — conta transações com data > essa
     * @return Quantidade de transações futuras da recorrência
     */
    long countByRecorrenciaIdAndDataTransacaoGreaterThan(Long recorrenciaId, LocalDate data);

    /**
     * Conta, numa consulta só, as parcelas futuras de cada recorrência de um usuário — usado pra
     * listar as recorrências sem uma consulta por recorrência.
     *
     * @param usuarioId Id do usuário dono das recorrências
     * @param data Data de referência (normalmente hoje) — conta transações com data > essa
     * @return Uma contagem por recorrência que ainda tiver parcelas futuras
     */
    @Query("""
            select new com.efinanceiro.repositorio.projecao.ParcelasPorRecorrencia(t.recorrencia.id, count(t))
            from Transacao t
            where t.usuario.id = :usuarioId and t.recorrencia is not null and t.dataTransacao > :data
            group by t.recorrencia.id
            """)
    List<ParcelasPorRecorrencia> contarParcelasFuturasPorRecorrencia(@Param("usuarioId") Long usuarioId,
                                                                     @Param("data") LocalDate data);

    /**
     * Lista as transações futuras (depois da data informada) de uma recorrência — usado pra
     * propagar edição de valor.
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — lista transações com data > essa
     * @return Lista de transações futuras da recorrência
     */
    List<Transacao> findByRecorrenciaIdAndDataTransacaoGreaterThan(Long recorrenciaId, LocalDate data);

    /**
     * Apaga todas as transações (passadas e futuras) de uma recorrência, num único DELETE — usado ao
     * excluir a recorrência.
     *
     * @param recorrenciaId Id da recorrência
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Transacao t where t.recorrencia.id = :recorrenciaId")
    void deleteByRecorrenciaId(@Param("recorrenciaId") Long recorrenciaId);

    /**
     * Apaga todas as transações de um usuário, num único DELETE — usado na exclusão do cadastro.
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Transacao t where t.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    /**
     * Move todas as transações de uma categoria pra outra, num único UPDATE — usado ao excluir uma
     * categoria personalizada.
     *
     * @param origem Categoria que vai ser excluída
     * @param destino Categoria fixa que recebe as transações
     * @return Quantidade de transações movidas
     */
    @Modifying(flushAutomatically = true)
    @Query("update Transacao t set t.categoria = :destino where t.categoria = :origem")
    int moverCategoria(@Param("origem") Categoria origem, @Param("destino") Categoria destino);
}
