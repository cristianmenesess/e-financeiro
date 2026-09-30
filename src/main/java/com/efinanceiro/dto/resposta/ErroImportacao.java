package com.efinanceiro.dto.resposta;

/**
 * Erro encontrado numa planilha importada. Linha 0 = problema do arquivo inteiro.
 */
public record ErroImportacao(int linha, String coluna, String mensagem) {
}
