package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequisicaoCategoria(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 40, message = "O nome da categoria deve ter no máximo 40 caracteres")
        String nome,

        @NotNull(message = "O tipo (entrada ou saída) é obrigatório")
        TipoTransacao tipo,

        @NotBlank(message = "O ícone é obrigatório")
        String icone,

        @NotBlank(message = "A cor é obrigatória")
        String tom
)
{}
