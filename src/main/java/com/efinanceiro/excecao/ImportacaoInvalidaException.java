package com.efinanceiro.excecao;

import com.efinanceiro.dto.resposta.ErroImportacao;

import java.util.List;

public class ImportacaoInvalidaException extends RuntimeException {

    private final List<ErroImportacao> erros;

    public ImportacaoInvalidaException(List<ErroImportacao> erros) {
        super("A planilha tem erros. Nada foi importado.");
        this.erros = List.copyOf(erros);
    }

    public List<ErroImportacao> getErros() {
        return erros;
    }
}
