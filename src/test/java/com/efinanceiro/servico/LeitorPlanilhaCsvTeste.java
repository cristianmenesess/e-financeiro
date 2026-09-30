package com.efinanceiro.servico;

import com.efinanceiro.dto.resposta.ErroImportacao;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LeitorPlanilhaCsvTeste {

    private final LeitorPlanilhaCsv leitor = new LeitorPlanilhaCsv();

    private PlanilhaLida ler(String csv) {
        return leitor.ler(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void leSeparadorPontoEVirgulaComCabecalhoEmQualquerOrdem() {
        PlanilhaLida planilha = ler("Valor;TIPO;Descrição;Data\n10,00;Saída;Mercado;01/03/2026\n");

        assertThat(planilha.erros()).isEmpty();
        assertThat(planilha.linhas()).hasSize(1);
        LinhaPlanilha linha = planilha.linhas().get(0);
        assertThat(linha.numero()).isEqualTo(2);
        assertThat(linha.campo(ColunasPlanilha.DESCRICAO)).isEqualTo("Mercado");
        assertThat(linha.campo(ColunasPlanilha.VALOR)).isEqualTo("10,00");
        assertThat(linha.campo(ColunasPlanilha.CATEGORIA)).isEmpty();
    }

    @Test
    void leSeparadorVirgulaComAspasEVirgulaDentroDoCampo() {
        PlanilhaLida planilha = ler("data,descricao,tipo,valor\r\n01/03/2026,\"Mercado, feira\",Saída,\"1.234,56\"\r\n");

        assertThat(planilha.erros()).isEmpty();
        assertThat(planilha.linhas().get(0).campo(ColunasPlanilha.DESCRICAO)).isEqualTo("Mercado, feira");
        assertThat(planilha.linhas().get(0).campo(ColunasPlanilha.VALOR)).isEqualTo("1.234,56");
    }

    @Test
    void aspasDuplicadasEQuebraDeLinhaDentroDasAspasMantemONumeroDaLinha() {
        PlanilhaLida planilha = ler("data;descrição;tipo;valor\n"
                + "01/03/2026;\"Presente \"\"especial\"\"\nsegunda linha\";Saída;10\n"
                + "02/03/2026;Padaria;Saída;5\n");

        assertThat(planilha.linhas()).hasSize(2);
        assertThat(planilha.linhas().get(0).campo(ColunasPlanilha.DESCRICAO)).isEqualTo("Presente \"especial\"\nsegunda linha");
        assertThat(planilha.linhas().get(1).numero()).isEqualTo(4);
    }

    @Test
    void ignoraBomLinhasVaziasEColunasDesconhecidas() {
        PlanilhaLida planilha = ler("\uFEFFdata;descrição;tipo;valor;observação\n\n01/03/2026;Mercado;Saída;10;qualquer\n;;;;\n");

        assertThat(planilha.erros()).isEmpty();
        assertThat(planilha.linhas()).hasSize(1);
        assertThat(planilha.linhas().get(0).numero()).isEqualTo(3);
    }

    @Test
    void leArquivoEmWindows1252() {
        byte[] conteudo = "data;descrição;tipo;valor\n01/03/2026;Alimentação;Saída;10\n".getBytes(Charset.forName("windows-1252"));

        PlanilhaLida planilha = leitor.ler(conteudo);

        assertThat(planilha.erros()).isEmpty();
        assertThat(planilha.linhas().get(0).campo(ColunasPlanilha.DESCRICAO)).isEqualTo("Alimentação");
    }

    @Test
    void colunaObrigatoriaAusenteERepetidaDaoErroNoCabecalho() {
        PlanilhaLida planilha = ler("data;descrição;tipo;tipo\n01/03/2026;Mercado;Saída;Saída\n");

        assertThat(planilha.linhas()).isEmpty();
        assertThat(planilha.erros()).containsExactlyInAnyOrder(
                new ErroImportacao(1, "tipo", "Coluna repetida no cabeçalho"),
                new ErroImportacao(1, "valor", "Coluna obrigatória ausente no cabeçalho"));
    }

    @Test
    void arquivoVazioOuSemLinhasDeDadosDaErro() {
        assertThat(ler("").erros()).containsExactly(new ErroImportacao(0, null, "A planilha está vazia"));
        assertThat(ler("data;descrição;tipo;valor\n\n").erros())
                .containsExactly(new ErroImportacao(0, null, "A planilha não tem nenhuma movimentação"));
    }

    @Test
    void maisDeDuasMilLinhasDaErro() {
        StringBuilder csv = new StringBuilder("data;descrição;tipo;valor\n");
        for (int i = 0; i < 2001; i++) {
            csv.append("01/03/2026;Linha ").append(i).append(";Saída;1\n");
        }

        PlanilhaLida planilha = ler(csv.toString());

        assertThat(planilha.linhas()).isEmpty();
        assertThat(planilha.erros()).containsExactly(new ErroImportacao(0, null, "A planilha tem mais de 2.000 linhas"));
    }

    @Test
    void removeApostrofoDeProtecaoContraFormula() {
        PlanilhaLida planilha = ler("data;descrição;tipo;valor\n01/03/2026;'=SOMA(A1);Saída;10\n02/03/2026;'texto normal;Saída;10\n");

        assertThat(planilha.linhas().get(0).campo(ColunasPlanilha.DESCRICAO)).isEqualTo("=SOMA(A1)");
        assertThat(planilha.linhas().get(1).campo(ColunasPlanilha.DESCRICAO)).isEqualTo("'texto normal");
    }

    @Test
    void aspasNaoFechadasDaoErroNaLinhaOndeAbriram() {
        PlanilhaLida planilha = ler("data;descrição;tipo;valor;obs\n01/03/2026;A;Saída;10;\"oops\n02/03/2026;B;Saída;20;\n");

        assertThat(planilha.linhas()).isEmpty();
        assertThat(planilha.erros()).containsExactly(new ErroImportacao(2, null, "Aspas não fechadas a partir desta linha"));
    }

    @Test
    void linhaComColunasDemaisDaErro() {
        String excesso = ";".repeat(60);
        PlanilhaLida planilha = ler("data;descrição;tipo;valor\n01/03/2026;A;Saída;10" + excesso + "\n");

        assertThat(planilha.linhas()).isEmpty();
        assertThat(planilha.erros()).containsExactly(new ErroImportacao(2, null, "Linha com colunas demais (máximo 50)"));
    }
}
