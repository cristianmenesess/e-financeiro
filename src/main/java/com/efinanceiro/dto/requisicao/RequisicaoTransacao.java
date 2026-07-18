package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoConta;
import com.efinanceiro.dominio.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RequisicaoTransacao(

        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor,

        @NotNull(message = "O tipo (entrada ou saída) é obrigatório")
        TipoTransacao tipo,

        @NotNull(message = "A conta (cpf ou pj) é obrigatória")
        TipoConta conta,

        @NotNull(message = "A categoria é obrigatória")
        Categoria categoria,

        Long cartaoId,

        LocalDate dataTransacao
)
{}