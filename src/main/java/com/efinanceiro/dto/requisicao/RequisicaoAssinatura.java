package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.AlcanceEdicao;
import com.efinanceiro.dominio.Periodicidade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RequisicaoAssinatura(

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 160, message = "A descrição deve ter no máximo 160 caracteres")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 10 dígitos inteiros e 2 casas decimais")
        BigDecimal valor,

        @NotNull(message = "A periodicidade (mensal ou anual) é obrigatória")
        Periodicidade periodicidade,

        @NotNull(message = "A categoria é obrigatória")
        Long categoriaId,

        @NotNull(message = "A conta é obrigatória")
        Long contaId,

        Long cartaoId,

        LocalDate dataInicio,

        // Só na edição: refazer só as próximas cobranças (padrão) ou todas
        AlcanceEdicao alcance
)
{}
