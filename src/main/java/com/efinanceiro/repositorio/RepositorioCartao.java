package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Cartao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioCartao extends JpaRepository<Cartao, Long> {

    /**
     * Lista todos os cartões pertencentes a um usuário.
     *
     * @param usuarioId Id do usuário dono dos cartões
     * @return Lista de cartões do usuário
     */
    List<Cartao> findByUsuarioId(Long usuarioId);

    /**
     * Busca um cartão pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id do cartão
     * @param usuarioId Id do usuário dono do cartão
     * @return Cartão encontrado, se existir e pertencer ao usuário
     */
    Optional<Cartao> findByIdAndUsuarioId(Long id, Long usuarioId);
}
