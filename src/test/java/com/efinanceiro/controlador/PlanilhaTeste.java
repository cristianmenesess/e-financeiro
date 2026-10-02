package com.efinanceiro.controlador;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanilhaTeste extends TesteIntegracao {

    private static final String CABECALHO = "data;descrição;tipo;valor;categoria;conta;cartão;parcela atual;total de parcelas\n";

    private MockMultipartHttpServletRequestBuilder enviar(String rota, String csv, Long contaPadraoId) {
        MockMultipartHttpServletRequestBuilder requisicao = multipart(HttpMethod.POST, rota)
                .file(new MockMultipartFile("arquivo", "planilha.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        requisicao.param("contaPadraoId", contaPadraoId.toString());
        return requisicao;
    }

    private MockMultipartFile ciclosCartoesNovos(String json) {
        return new MockMultipartFile("cartoesNovos", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private String previa(String token, String csv) throws Exception {
        return mockMvc.perform(comToken(enviar("/api/planilhas/previa", csv, idContaPadrao(token)), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int totalDeTransacoes(String token) throws Exception {
        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        List<Object> itens = JsonPath.read(lista, "$");
        return itens.size();
    }

    @Test
    void previaSemErrosResumeENaoGravaNada() throws Exception {
        String token = cadastrarUsuario();

        String resposta = previa(token, CABECALHO
                + "01/03/2026;Mercado;Saída;150,00;Alimentação;;;;\n"
                + "10/03/2026;Geladeira;Saída;300,00;Moradia;;Nubank;3;10\n");

        assertThat((List<Object>) JsonPath.read(resposta, "$.erros")).isEmpty();
        assertThat((Integer) JsonPath.read(resposta, "$.lancamentosAvulsos")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(resposta, "$.recorrencias")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(resposta, "$.novosCartoes")).containsExactly("Nubank");
        assertThat((List<String>) JsonPath.read(resposta, "$.novasCategorias")).isEmpty();
        assertThat((String) JsonPath.read(resposta, "$.linhas[0].conta")).isEqualTo("Pessoal");
        assertThat(totalDeTransacoes(token)).isZero();
    }

    @Test
    void previaApontaLinhaColunaEMotivoDeCadaErro() throws Exception {
        String token = cadastrarUsuario();

        String resposta = previa(token, CABECALHO
                + "31/02/2026;Mercado;Saída;10,00;;;;;\n"
                + "01/03/2026;Mercado;Talvez;10,00;;;;;\n"
                + "01/03/2026;Mercado;Saída;-50,00;;;;;\n"
                + "01/03/2026;Mercado;Saída;abc;;;;;\n"
                + "01/03/2026;Salário;Entrada;3000;Moradia;;;;\n"
                + "01/03/2026;Carro;Saída;100;;;;2;\n"
                + "01/03/2026;;Saída;10;;;;;\n"
                + "01/03/2026;Curso;Saída;10;;;;11;10\n"
                + "01/03/2026;Troco;Saída;10,005;;;;;\n");

        List<Map<String, Object>> erros = JsonPath.read(resposta, "$.erros");
        assertThat(erros).extracting(erro -> erro.get("linha") + "|" + erro.get("coluna") + "|" + erro.get("mensagem"))
                .containsExactlyInAnyOrder(
                        "2|data|'31/02/2026' não é uma data válida (use dd/mm/aaaa)",
                        "3|tipo|'Talvez' não é um tipo válido (use Entrada ou Saída)",
                        "4|valor|Use valor positivo; entrada ou saída vem da coluna tipo",
                        "5|valor|'abc' não é um valor válido",
                        "6|categoria|A categoria 'Moradia' é de saída, mas a linha é de entrada",
                        "7|total de parcelas|Informe também o total de parcelas",
                        "8|descrição|A descrição é obrigatória",
                        "9|parcela atual|A parcela atual (11) é maior que o total de parcelas (10)",
                        "10|valor|O valor deve ter no máximo 2 casas decimais");
    }

    @Test
    void importacaoComErroDevolve400ENaoGravaNada() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO
                        + "01/03/2026;Mercado;Saída;150,00;Pet;Empresa;Nubank;;\n"
                        + "31/02/2026;Padaria;Saída;10,00;;;;;\n", idContaPadrao(token)), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A planilha tem erros. Nada foi importado."))
                .andExpect(jsonPath("$.erros[0].linha").value(3))
                .andExpect(jsonPath("$.erros[0].coluna").value("data"));

        assertThat(totalDeTransacoes(token)).isZero();
        mockMvc.perform(comToken(get("/api/categorias"), token)).andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(comToken(get("/api/contas"), token)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(comToken(get("/api/cartoes"), token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void importacaoCriaCategoriaContaECartaoQueNaoExistem() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO
                        + "01/03/2026;Ração;Saída;150,00;Pet;Empresa;Nubank;;\n"
                        + "05/03/2026;Freela;Entrada;1.200,00;Freelance;;;;\n", idContaPadrao(token))
                        .file(ciclosCartoesNovos("[{\"nome\": \"nubank\", \"diaFechamento\": 3, \"diaVencimento\": 10}]")), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(2))
                .andExpect(jsonPath("$.categoriasCriadas").value(2))
                .andExpect(jsonPath("$.contasCriadas").value(1))
                .andExpect(jsonPath("$.cartoesCriados").value(1));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Ração')].nomeCategoria")).containsExactly("Pet");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Ração')].nomeConta")).containsExactly("Empresa");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Ração')].nomeCartao")).containsExactly("Nubank");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Ração')].dataCompra")).containsExactly("2026-03-01");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Ração')].dataTransacao")).containsExactly("2026-03-10");
        assertThat((List<Double>) JsonPath.read(lista, "$[?(@.descricao == 'Freela')].valor")).containsExactly(1200.0);
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.descricao == 'Freela')].nomeConta")).containsExactly("Pessoal");

        String categorias = mockMvc.perform(comToken(get("/api/categorias"), token)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(categorias, "$[?(@.nome == 'Pet')].tipo")).containsExactly("SAIDA");
        assertThat((List<String>) JsonPath.read(categorias, "$[?(@.nome == 'Freelance')].tipo")).containsExactly("ENTRADA");
    }

    @Test
    void cartaoNovoSemFechamentoEVencimentoDevolve400ENaoGravaNada() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO
                        + "01/03/2026;Ração;Saída;150,00;Pet;;Nubank;;\n"
                        + "02/03/2026;Mercado;Saída;80,00;;;Inter;;\n", idContaPadrao(token))
                        .file(ciclosCartoesNovos("[{\"nome\": \"Inter\", \"diaFechamento\": 32, \"diaVencimento\": 5}]")), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].coluna").value("cartão"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("Informe o fechamento e o vencimento (dia de 1 a 31) do cartão novo 'Nubank'"))
                .andExpect(jsonPath("$.erros[1].mensagem").value("Informe o fechamento e o vencimento (dia de 1 a 31) do cartão novo 'Inter'"));

        assertThat(totalDeTransacoes(token)).isZero();
        mockMvc.perform(comToken(get("/api/categorias"), token)).andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(comToken(get("/api/cartoes"), token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void parcelasNoCartaoTrazemADataDaCompraECaemNasFaturas() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        String csv = CABECALHO
                + "10/03/2026;TV;Saída;100,00;;;Nubank;1;3\n"
                + "10/03/2026;TV;Saída;100,00;;;Nubank;2;3\n";

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", csv, contaId)
                        .file(ciclosCartoesNovos("[{\"nome\": \"Nubank\", \"diaFechamento\": 3, \"diaVencimento\": 10}]")), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorrencias").value(1))
                .andExpect(jsonPath("$.parcelasGeradas").value(3));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataTransacao")).containsExactly("2026-06-10", "2026-05-10", "2026-04-10");
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataCompra")).containsOnly("2026-03-10");

        String resposta = previa(token, csv);
        assertThat((List<Boolean>) JsonPath.read(resposta, "$.linhas[*].possivelDuplicado")).containsOnly(true);
    }

    @Test
    void parcelaTresDeDezViraRecorrenciaComInicioDoisMesesAntes() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar",
                        CABECALHO + "10/03/2026;Geladeira;Saída;300,00;Moradia;;;3;10\n", idContaPadrao(token)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorrencias").value(1))
                .andExpect(jsonPath("$.parcelasGeradas").value(10));

        // Relógio de teste: 15/03/2026 — parcelas de abril a outubro ainda estão por vir
        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$[0].dataInicio").value("2026-01-10"))
                .andExpect(jsonPath("$[0].totalParcelas").value(10))
                .andExpect(jsonPath("$[0].parcelasRestantes").value(7));
    }

    @Test
    void linhasDaMesmaCompraParceladaViramUmaRecorrenciaSo() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO
                        + "10/01/2026;Notebook;Saída;500,00;Outro;;;1;3\n"
                        + "10/02/2026;notebook;Saída;500,00;Outro;;;2;3\n"
                        + "10/03/2026;Notebook;Saída;500,00;Outro;;;3;3\n", idContaPadrao(token)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorrencias").value(1))
                .andExpect(jsonPath("$.parcelasGeradas").value(3));

        assertThat(totalDeTransacoes(token)).isEqualTo(3);
    }

    @Test
    void possivelDuplicadoEMarcadoPuladoPorPadraoEIncluidoQuandoPedido() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        criarTransacao(token, contaId, "SAIDA", "10.00", "2026-03-01", null);   // descrição "Lançamento"

        String csv = CABECALHO + "01/03/2026; lançamento ;Saída;10,00;;;;;\n";

        String resposta = previa(token, csv);
        assertThat((Boolean) JsonPath.read(resposta, "$.linhas[0].possivelDuplicado")).isTrue();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", csv, contaId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(0))
                .andExpect(jsonPath("$.duplicadosPulados").value(1));
        assertThat(totalDeTransacoes(token)).isEqualTo(1);

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", csv, contaId).param("linhasDuplicadasIncluidas", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(1))
                .andExpect(jsonPath("$.duplicadosPulados").value(0));
        assertThat(totalDeTransacoes(token)).isEqualTo(2);
    }

    @Test
    void contaPadraoDeOutroUsuarioDevolve404() throws Exception {
        String token = cadastrarUsuario();
        Long contaDeOutro = idContaPadrao(cadastrarUsuario());

        mockMvc.perform(comToken(enviar("/api/planilhas/previa", CABECALHO + "01/03/2026;X;Saída;1;;;;;\n", contaDeOutro), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Conta não encontrada"));
    }

    @Test
    void aceitaSeparadorVirgulaEValorComPonto() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar",
                        "data,descricao,tipo,valor\n01/03/2026,\"Mercado, feira\",Saida,1234.56\n", idContaPadrao(token)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(1));

        mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andExpect(jsonPath("$[0].descricao").value("Mercado, feira"))
                .andExpect(jsonPath("$[0].valor").value(1234.56));
    }

    @Test
    void importacaoQuePassariaDoLimiteDeCategoriasDaErro() throws Exception {
        String token = cadastrarUsuario();

        for (int i = 1; i <= 49; i++) {
            mockMvc.perform(comToken(post("/api/categorias"), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\": \"Cat %d\", \"tipo\": \"SAIDA\", \"icone\": \"car\", \"tom\": \"brand\"}".formatted(i)))
                    .andExpect(status().isCreated());
        }

        String resposta = previa(token, CABECALHO
                + "01/03/2026;A;Saída;1;Nova 1;;;;\n"
                + "01/03/2026;B;Saída;1;Nova 2;;;;\n");

        assertThat((List<String>) JsonPath.read(resposta, "$.erros[*].mensagem"))
                .containsExactly("A importação criaria 2 categorias e passaria do limite de 50 categorias personalizadas");
        assertThat((List<Integer>) JsonPath.read(resposta, "$.erros[*].linha")).containsExactly(0);
    }

    @Test
    void modeloBaixaComCabecalhoEExemplosQueImportamSemErro() throws Exception {
        String token = cadastrarUsuario();

        byte[] modelo = mockMvc.perform(comToken(get("/api/planilhas/modelo"), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"modelo-importacao.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = new String(modelo, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("\uFEFFdata;descrição;tipo;valor;categoria;conta;cartão;parcela atual;total de parcelas");

        String resposta = mockMvc.perform(comToken(multipart(HttpMethod.POST, "/api/planilhas/previa")
                        .file(new MockMultipartFile("arquivo", "modelo.csv", "text/csv", modelo))
                        .param("contaPadraoId", idContaPadrao(token).toString()), token))
                .andReturn().getResponse().getContentAsString();

        assertThat((List<Object>) JsonPath.read(resposta, "$.erros")).isEmpty();
        assertThat((Integer) JsonPath.read(resposta, "$.lancamentosAvulsos")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(resposta, "$.recorrencias")).isEqualTo(1);
    }

    @Test
    void exportacaoGeraCsvNoFormatoDoModeloEReimportaIgualEmOutroUsuario() throws Exception {
        String tokenOrigem = cadastrarUsuario();
        Long contaOrigem = idContaPadrao(tokenOrigem);

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO
                        + "01/03/2026;=HYPERLINK(\"x\");Saída;10,00;Pet;;;;\n"
                        + "02/03/2026;\"Almoço; sobremesa\";Saída;25,50;Alimentação;;;;\n"
                        + "05/03/2026;Salário;Entrada;3.000,00;;;;;\n"
                        + "10/03/2026;Geladeira;Saída;300,00;Moradia;;Nubank;2;4\n", contaOrigem)
                        .file(ciclosCartoesNovos("[{\"nome\": \"Nubank\", \"diaFechamento\": 3, \"diaVencimento\": 10}]")), tokenOrigem))
                .andExpect(status().isOk());

        byte[] exportado = mockMvc.perform(comToken(get("/api/planilhas/exportar"), tokenOrigem))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"e-financeiro-movimentacoes-")))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = new String(exportado, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("\uFEFFdata;descrição;tipo;valor;categoria;conta;cartão;parcela atual;total de parcelas\r\n");
        assertThat(texto).contains("01/03/2026;\"'=HYPERLINK(\"\"x\"\")\";Saída;10,00;Pet;Pessoal;;;");
        assertThat(texto).contains("\"Almoço; sobremesa\"");
        assertThat(texto).contains("05/03/2026;Salário;Entrada;3000,00;Renda;Pessoal;;;");
        assertThat(texto).contains("10/03/2026;Geladeira;Saída;300,00;Moradia;Pessoal;Nubank;2;4");

        String tokenDestino = cadastrarUsuario();

        mockMvc.perform(comToken(multipart(HttpMethod.POST, "/api/planilhas/importar")
                        .file(new MockMultipartFile("arquivo", "exportado.csv", "text/csv", exportado))
                        .file(ciclosCartoesNovos("[{\"nome\": \"Nubank\", \"diaFechamento\": 3, \"diaVencimento\": 10}]"))
                        .param("contaPadraoId", idContaPadrao(tokenDestino).toString()), tokenDestino))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(3))
                .andExpect(jsonPath("$.recorrencias").value(1))
                .andExpect(jsonPath("$.parcelasGeradas").value(4));

        assertThat(totalDeTransacoes(tokenDestino)).isEqualTo(totalDeTransacoes(tokenOrigem));

        mockMvc.perform(comToken(get("/api/transacoes"), tokenDestino))
                .andExpect(jsonPath("$[?(@.descricao == '=HYPERLINK(\"x\")')]").exists());
    }

    @Test
    void importacaoQueGerariaLancamentosDemaisDaErro() throws Exception {
        String token = cadastrarUsuario();
        StringBuilder csv = new StringBuilder(CABECALHO);

        for (int i = 0; i < 30; i++) {
            csv.append("01/03/2026;Compra ").append(i).append(";Saída;10,00;;;;1;360\n");
        }

        String resposta = previa(token, csv.toString());

        assertThat((List<String>) JsonPath.read(resposta, "$.erros[*].mensagem"))
                .containsExactly("A importação geraria 10800 lançamentos (limite de 10.000)");
        assertThat((List<Integer>) JsonPath.read(resposta, "$.erros[*].linha")).containsExactly(0);
    }

    @Test
    void valorEmNotacaoCientificaOuComSinalMaisEInvalido() throws Exception {
        String token = cadastrarUsuario();

        String resposta = previa(token, CABECALHO
                + "01/03/2026;A;Saída;1e3;;;;;\n"
                + "01/03/2026;B;Saída;+10;;;;;\n"
                + "01/03/2026;C;Saída;\"1,5e3\";;;;;\n");

        assertThat((List<String>) JsonPath.read(resposta, "$.erros[*].mensagem")).containsExactly(
                "'1e3' não é um valor válido", "'+10' não é um valor válido", "'1,5e3' não é um valor válido");
    }

    @Test
    void aceitaDataSemZeroAEsquerda() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(enviar("/api/planilhas/importar", CABECALHO + "1/3/2026;Mercado;Saída;10,00;;;;;\n", idContaPadrao(token)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentosAvulsos").value(1));

        mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andExpect(jsonPath("$[0].dataTransacao").value("2026-03-01"));
    }
}
