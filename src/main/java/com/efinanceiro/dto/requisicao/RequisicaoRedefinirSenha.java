package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoRedefinirSenha(

        @NotBlank(message = "O token é obrigatório")
        String token,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        String novaSenha
)
{}
