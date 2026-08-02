package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequisicaoConta(

        @NotBlank(message = "O nome da conta é obrigatório")
        String nome,

        @NotBlank(message = "A cor de fundo é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor de fundo deve ser um hexadecimal no formato #RRGGBB")
        String corFundo,

        @NotBlank(message = "A cor do texto é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor do texto deve ser um hexadecimal no formato #RRGGBB")
        String corTexto
)
{}
