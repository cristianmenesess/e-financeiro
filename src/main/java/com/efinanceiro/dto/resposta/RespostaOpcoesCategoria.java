package com.efinanceiro.dto.resposta;

import java.util.List;

public record RespostaOpcoesCategoria(
        List<String> icones,
        List<String> tons
) {
}
