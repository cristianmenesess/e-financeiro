package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.TipoConta;
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
     * Lista as transações de um usuário filtradas por tipo de conta, ordenadas da mais recente pra mais antiga.
     *
     * @param usuarioId Id do usuário dono das transações
     * @param conta Tipo de conta (cpf ou pj) para filtrar
     * @return Lista de transações filtradas
     */
    List<Transacao> findByUsuarioIdAndContaOrderByDataTransacaoDesc(Long usuarioId, TipoConta conta);

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
}
