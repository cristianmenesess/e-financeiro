package com.efinanceiro.dto.resposta;

import java.util.List;

public record RespostaPreviaImportacao(
        List<LinhaPrevia> linhas,
        List<ErroImportacao> erros,
        List<String> novasCategorias,
        List<String> novasContas,
        List<String> novosCartoes,
        int lancamentosAvulsos,
        int recorrencias
) {
}
