package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Recorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioRecorrencia extends JpaRepository<Recorrencia, Long> {

    /**
     * Lista todas as recorrências pertencentes a um usuário.
     *
     * @param usuarioId Id do usuário dono das recorrências
     * @return Lista de recorrências do usuário
     */
    List<Recorrencia> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma recorrência pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da recorrência
     * @param usuarioId Id do usuário dono da recorrência
     * @return Recorrência encontrada, se existir e pertencer ao usuário
     */
    Optional<Recorrencia> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Apaga todas as recorrências vinculadas a uma conta — usado ao excluir a conta em cascata
     * (a Recorrencia nunca é apagada pela própria API de recorrências, só suas parcelas futuras,
     * então isso é necessário pra não deixar uma FK pra uma conta que não existe mais).
     *
     * @param contaId Id da conta cujas recorrências serão apagadas
     */
    void deleteByContaId(Long contaId);
}
