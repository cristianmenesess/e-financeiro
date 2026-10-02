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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Gasto que se repete todo mês ou todo ano, sem data de fim, até ser cancelado. As cobranças são
 * transações lançadas aos poucos, sempre até um horizonte à frente de hoje.
 */
@Entity
@Table(name = "assinaturas")
@Getter
@Setter
@NoArgsConstructor
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 160)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Periodicidade periodicidade;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "gerada_ate")
    private LocalDate geradaAte;

    @Column(name = "cancelada_em")
    private LocalDate canceladaEm;

    // Evita cobrança em dobro quando duas requisições lançam as próximas cobranças ao mesmo tempo
    @Version
    private Long versao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        this.criadoEm = Instant.now();
    }

    /**
     * Data da cobrança de número {@code indice} (0 é a primeira). Dia que o mês não tem vira o
     * último dia do mês, sempre contado a partir da data de início.
     *
     * @param indice Posição da cobrança, a partir de 0
     * @return Data da cobrança
     */
    public LocalDate dataDaCobranca(long indice) {
        return periodicidade == Periodicidade.ANUAL ? dataInicio.plusYears(indice) : dataInicio.plusMonths(indice);
    }
}
