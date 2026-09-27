package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RequisicaoRecorrencia(

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 160, message = "A descrição deve ter no máximo 160 caracteres")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 10 dígitos inteiros e 2 casas decimais")
        BigDecimal valor,

        @NotNull(message = "O tipo (entrada ou saída) é obrigatório")
        TipoTransacao tipo,

        @NotNull(message = "A categoria é obrigatória")
        Long categoriaId,

        @NotNull(message = "A conta é obrigatória")
        Long contaId,

        Long cartaoId,

        @NotNull(message = "A quantidade de parcelas é obrigatória")
        @Min(value = 1, message = "A quantidade de parcelas deve ser no mínimo 1")
        @Max(value = 360, message = "A quantidade de parcelas deve ser no máximo 360")
        Integer totalParcelas,

        LocalDate dataInicio
)
{}
