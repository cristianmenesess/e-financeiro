package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequisicaoCartao(

        @NotBlank(message = "O nome do cartão é obrigatório")
        @Size(max = 120, message = "O nome do cartão deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "A cor de fundo é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor de fundo deve ser um hexadecimal no formato #RRGGBB")
        String corFundo,

        @NotBlank(message = "A cor do texto é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor do texto deve ser um hexadecimal no formato #RRGGBB")
        String corTexto,

        @NotNull(message = "O dia de fechamento da fatura é obrigatório")
        @Min(value = 1, message = "O dia de fechamento deve ser entre 1 e 31")
        @Max(value = 31, message = "O dia de fechamento deve ser entre 1 e 31")
        Integer diaFechamento,

        @NotNull(message = "O dia de vencimento da fatura é obrigatório")
        @Min(value = 1, message = "O dia de vencimento deve ser entre 1 e 31")
        @Max(value = 31, message = "O dia de vencimento deve ser entre 1 e 31")
        Integer diaVencimento
)
{}
