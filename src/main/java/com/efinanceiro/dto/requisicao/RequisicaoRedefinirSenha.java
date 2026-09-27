package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dto.validacao.TamanhoMaximoEmBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoRedefinirSenha(

        @NotBlank(message = "O token é obrigatório")
        @Size(max = 64, message = "Link de redefinição inválido ou expirado")
        String token,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        @TamanhoMaximoEmBytes(value = 72, message = "A senha deve ter no máximo 72 caracteres")
        String novaSenha
)
{}
