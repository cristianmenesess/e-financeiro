package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioConta extends JpaRepository<Conta, Long> {

    /**
     * Lista todas as contas pertencentes a um usuário.
     *
     * @param usuarioId Id do usuário dono das contas
     * @return Lista de contas do usuário
     */
    List<Conta> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma conta pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da conta
     * @param usuarioId Id do usuário dono da conta
     * @return Conta encontrada, se existir e pertencer ao usuário
     */
    Optional<Conta> findByIdAndUsuarioId(Long id, Long usuarioId);
}
