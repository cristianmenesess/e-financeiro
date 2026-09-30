package com.efinanceiro.servico;

import com.efinanceiro.dto.resposta.ErroImportacao;

import java.util.List;

/**
 * Resultado da leitura do arquivo: linhas de dados e erros de estrutura (cabeçalho, limites).
 */
public record PlanilhaLida(List<LinhaPlanilha> linhas, List<ErroImportacao> erros) {
}
