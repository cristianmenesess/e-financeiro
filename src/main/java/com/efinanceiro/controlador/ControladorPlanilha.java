package com.efinanceiro.controlador;

import com.efinanceiro.dto.resposta.RespostaImportacao;
import com.efinanceiro.dto.resposta.RespostaPreviaImportacao;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.servico.ServicoExportacaoPlanilha;
import com.efinanceiro.servico.ServicoImportacaoPlanilha;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/planilhas")
public class ControladorPlanilha {

    private static final long TAMANHO_MAXIMO = 2L * 1024 * 1024;
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ServicoImportacaoPlanilha servicoImportacaoPlanilha;
    private final ServicoExportacaoPlanilha servicoExportacaoPlanilha;
    private final Clock relogio;

    public ControladorPlanilha(ServicoImportacaoPlanilha servicoImportacaoPlanilha,
                               ServicoExportacaoPlanilha servicoExportacaoPlanilha,
                               Clock relogio) {
        this.servicoImportacaoPlanilha = servicoImportacaoPlanilha;
        this.servicoExportacaoPlanilha = servicoExportacaoPlanilha;
        this.relogio = relogio;
    }

    /**
     * Baixa o modelo de planilha pra importação.
     *
     * @return Arquivo CSV com cabeçalho e exemplos
     */
    @GetMapping("/modelo")
    public ResponseEntity<byte[]> baixarModelo() {
        return arquivo(servicoExportacaoPlanilha.gerarModelo(), "modelo-importacao.csv");
    }

    /**
     * Analisa uma planilha sem gravar nada.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param arquivo Arquivo CSV no campo multipart "arquivo"
     * @param contaPadraoId Conta usada nas linhas sem a coluna conta
     * @return Prévia da importação
     */
    @PostMapping("/previa")
    public ResponseEntity<RespostaPreviaImportacao> gerarPrevia(Authentication autenticacao,
                                                                @RequestPart("arquivo") MultipartFile arquivo,
                                                                @RequestParam("contaPadraoId") Long contaPadraoId) {
        return ResponseEntity.ok(servicoImportacaoPlanilha.gerarPrevia(autenticacao.getName(), lerArquivo(arquivo), contaPadraoId));
    }

    /**
     * Importa uma planilha (tudo ou nada).
     *
     * @param autenticacao Autenticação do usuário atual
     * @param arquivo Arquivo CSV no campo multipart "arquivo"
     * @param contaPadraoId Conta usada nas linhas sem a coluna conta
     * @param linhasDuplicadasIncluidas Linhas marcadas como possível duplicado que devem ser importadas
     * @return Resumo da importação
     */
    @PostMapping("/importar")
    public ResponseEntity<RespostaImportacao> importar(Authentication autenticacao,
                                                       @RequestPart("arquivo") MultipartFile arquivo,
                                                       @RequestParam("contaPadraoId") Long contaPadraoId,
                                                       @RequestParam(value = "linhasDuplicadasIncluidas", required = false) List<Integer> linhasDuplicadasIncluidas) {
        return ResponseEntity.ok(servicoImportacaoPlanilha.importar(autenticacao.getName(), lerArquivo(arquivo), contaPadraoId,
                linhasDuplicadasIncluidas == null ? new HashSet<>() : new HashSet<>(linhasDuplicadasIncluidas)));
    }

    /**
     * Exporta todas as movimentações do usuário em CSV.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Arquivo CSV no formato do modelo
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(Authentication autenticacao) {
        return arquivo(servicoExportacaoPlanilha.exportar(autenticacao.getName()),
                "e-financeiro-movimentacoes-" + LocalDate.now(relogio) + ".csv");
    }

    private byte[] lerArquivo(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new DadosInvalidosException("Selecione um arquivo CSV");
        }

        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new DadosInvalidosException("A planilha deve ter no máximo 2 MB");
        }

        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new DadosInvalidosException("Não foi possível ler o arquivo enviado");
        }
    }

    private ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(conteudo);
    }
}
