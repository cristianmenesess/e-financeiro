package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.TipoTransacao;

public record RespostaCategoria(
        Long id,
        String nome,
        TipoTransacao tipo,
        String icone,
        String tom,
        boolean fixa
) {
}
