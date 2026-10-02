package com.efinanceiro.servico;

import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera planilhas CSV no modelo do sistema: o modelo vazio pra importação e a exportação das
 * movimentações do usuário.
 */
@Service
@Transactional(readOnly = true)
public class ServicoExportacaoPlanilha {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEPARADOR = ";";
    private static final String FIM_DE_LINHA = "\r\n";
    private static final String BOM = "\uFEFF";

    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;

    public ServicoExportacaoPlanilha(RepositorioTransacao repositorioTransacao, BuscadorRecursosDoUsuario buscadorRecursosDoUsuario) {
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
    }

    /**
     * Modelo de planilha: cabeçalho e dois exemplos (um lançamento avulso e uma compra parcelada).
     *
     * @return Bytes do CSV (UTF-8 com BOM)
     */
    public byte[] gerarModelo() {
        return (BOM + cabecalho()
                + String.join(SEPARADOR, "05/09/2026", "Salário de setembro", "Entrada", "3500,00", "Renda", "", "", "", "") + FIM_DE_LINHA
                + String.join(SEPARADOR, "10/07/2026", "Geladeira", "Saída", "300,00", "Moradia", "", "Nubank", "3", "10") + FIM_DE_LINHA)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Exporta todas as movimentações do usuário, da mais antiga pra mais recente, no mesmo formato
     * aceito pela importação.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Bytes do CSV (UTF-8 com BOM)
     */
    public byte[] exportar(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        List<Transacao> transacoes = repositorioTransacao.findByUsuarioId(usuario.getId(),
                Pageable.unpaged(Sort.by(Sort.Order.asc("dataTransacao"), Sort.Order.asc("id")))).getContent();

        String linhas = transacoes.stream().map(this::paraLinha).collect(Collectors.joining());
        return (BOM + cabecalho() + linhas).getBytes(StandardCharsets.UTF_8);
    }

    private String cabecalho() {
        return String.join(SEPARADOR, ColunasPlanilha.ORDEM) + FIM_DE_LINHA;
    }

    private String paraLinha(Transacao transacao) {
        boolean parcela = transacao.getRecorrencia() != null;

        // Com cartão, sai a data da compra — a mesma que a importação espera
        LocalDate data = transacao.getDataCompra() != null ? transacao.getDataCompra() : transacao.getDataTransacao();

        return String.join(SEPARADOR,
                data.format(FORMATO_DATA),
                celula(transacao.getDescricao()),
                transacao.getTipo() == TipoTransacao.ENTRADA ? "Entrada" : "Saída",
                transacao.getValor().setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','),
                celula(transacao.getCategoria().getNome()),
                celula(transacao.getConta().getNome()),
                transacao.getCartao() != null ? celula(transacao.getCartao().getNome()) : "",
                parcela && transacao.getNumeroParcela() != null ? transacao.getNumeroParcela().toString() : "",
                parcela ? transacao.getRecorrencia().getTotalParcelas().toString() : "") + FIM_DE_LINHA;
    }

    // Protege contra fórmula (=, +, -, @, tab ou retorno de carro no início) e coloca entre aspas o que tiver ; " ou quebra de linha
    private String celula(String valor) {
        String protegido = !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;

        if (protegido.contains(SEPARADOR) || protegido.contains("\"") || protegido.contains("\n") || protegido.contains("\r")) {
            return "\"" + protegido.replace("\"", "\"\"") + "\"";
        }

        return protegido;
    }
}
