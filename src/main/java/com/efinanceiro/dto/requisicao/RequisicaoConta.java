package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequisicaoConta(

        @NotBlank(message = "O nome da conta é obrigatório")
        @Size(max = 120, message = "O nome da conta deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "A cor de fundo é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor de fundo deve ser um hexadecimal no formato #RRGGBB")
        String corFundo,

        @NotBlank(message = "A cor do texto é obrigatória")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor do texto deve ser um hexadecimal no formato #RRGGBB")
        String corTexto
)
{}
