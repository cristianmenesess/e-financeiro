package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoExclusaoConta(

        @NotBlank(message = "A senha é obrigatória")
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senha
)
{}
