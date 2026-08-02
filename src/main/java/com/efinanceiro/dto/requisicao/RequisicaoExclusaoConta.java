package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;

public record RequisicaoExclusaoConta(

        @NotBlank(message = "A senha é obrigatória")
        String senha
)
{}
