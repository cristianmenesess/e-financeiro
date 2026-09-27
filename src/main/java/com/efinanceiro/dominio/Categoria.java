package com.efinanceiro.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nulo nas categorias fixas do sistema, que valem pra todos os usuários
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Só nas fixas (RENDA, DESPESA, ALIMENTACAO, MORADIA, OUTRO): o código acha "Renda"/"Outro" sem depender de id
    @Column(length = 20, unique = true)
    private String codigo;

    @Column(nullable = false, length = 40)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacao tipo;

    @Column(nullable = false, length = 40)
    private String icone;

    @Column(nullable = false, length = 20)
    private String tom;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    /**
     * Indica se é uma categoria fixa do sistema (sem dono), que não pode ser editada nem excluída.
     *
     * @return true se for fixa
     */
    public boolean isFixa() {
        return usuario == null;
    }

    @PrePersist
    private void aoPersistir() {
        this.criadoEm = Instant.now();
    }
}
