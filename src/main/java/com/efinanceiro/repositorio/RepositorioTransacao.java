package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepositorioTransacao extends JpaRepository<Transacao, Long> {

    /**
     * Lista todas as transações de um usuário, ordenadas da mais recente pra mais antiga.
     *
     * @param usuarioId Id do usuário dono das transações
     * @return Lista de transações
     */
    List<Transacao> findByUsuarioIdOrderByDataTransacaoDesc(Long usuarioId);

    /**
     * Lista as transações de um usuário filtradas por conta, ordenadas da mais recente pra mais antiga.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param contaId Id da conta para filtrar
     * @return Lista de transações filtradas
     */
    List<Transacao> findByUsuarioIdAndContaIdOrderByDataTransacaoDesc(Long usuarioId, Long contaId);

    /**
     * Busca uma transação pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da transação
     * @param usuarioId Id do usuário dono da transação
     * @return Transação encontrada, se existir e pertencer ao usuário
     */
    Optional<Transacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Lista as transações de um cartão, de um tipo específico, dentro de um intervalo de datas —
     * usado pra calcular o gasto do mês de cada cartão.
     *
     * @param cartaoId Id do cartão
     * @param tipo Tipo da transação (sempre SAIDA nesse uso)
     * @param inicio Data inicial do intervalo (inclusive)
     * @param fim Data final do intervalo (inclusive)
     * @return Lista de transações do cartão no período
     */
    List<Transacao> findByCartaoIdAndTipoAndDataTransacaoBetween(Long cartaoId, TipoTransacao tipo, LocalDate inicio, LocalDate fim);

    /**
     * Apaga todas as transações vinculadas a uma conta — usado ao excluir a conta em cascata.
     *
     * @param contaId Id da conta cujas transações serão apagadas
     */
    void deleteByContaId(Long contaId);

    /**
     * Conta quantas transações de uma recorrência ainda estão no futuro (não passaram) —
     * usado pra calcular "parcelas restantes".
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — conta transações com data >= essa
     * @return Quantidade de transações futuras da recorrência
     */
    long countByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);

    /**
     * Lista as transações futuras de uma recorrência — usado pra propagar edição de valor.
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — lista transações com data >= essa
     * @return Lista de transações futuras da recorrência
     */
    List<Transacao> findByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);

    /**
     * Apaga as transações futuras de uma recorrência — usado ao cancelar parcelas futuras.
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — apaga transações com data >= essa
     */
    void deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);
}
