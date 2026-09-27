package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dto.validacao.TamanhoMaximoEmBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoTrocaSenha(

        @NotBlank(message = "A senha atual é obrigatória")
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senhaAtual,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres")
        @TamanhoMaximoEmBytes(value = 72, message = "A nova senha deve ter no máximo 72 caracteres")
        String novaSenha
)
{}
