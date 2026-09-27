package com.efinanceiro.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "token_redefinicao_hash", length = 64)
    private String tokenRedefinicaoHash;

    @Column(name = "token_redefinicao_expira_em")
    private Instant tokenRedefinicaoExpiraEm;

    // Marca a última mudança de credenciais (senha OU e-mail) e também o cadastro: tokens JWT
    // emitidos antes desse instante são recusados pelo filtro. Só é nulo em usuários antigos
    // (anteriores à V6) que nunca trocaram senha nem e-mail
    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @PrePersist
    private void aoPersistir() {
        this.criadoEm = Instant.now();
    }
}
