package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sem o tipo: trocar entrada <-> saída deixaria as movimentações da categoria com o tipo errado
public record RequisicaoAtualizacaoCategoria(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 40, message = "O nome da categoria deve ter no máximo 40 caracteres")
        String nome,

        @NotBlank(message = "O ícone é obrigatório")
        String icone,

        @NotBlank(message = "A cor é obrigatória")
        String tom
)
{}
