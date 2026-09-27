package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoAtualizacaoPerfil(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email,

        // Obrigatória só quando o e-mail muda — conferido no serviço
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senhaAtual
)
{}
