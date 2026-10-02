package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relógio de teste em 15/03/2026; as cobranças ficam lançadas até 15/03/2027.
 */
class AssinaturaTeste extends TesteIntegracao {

    private String corpo(Long contaId, Long cartaoId, String valor, String periodicidade, String dataInicio, String alcance) {
        return """
                {"descricao": "Streaming", "valor": %s, "periodicidade": "%s", "categoriaId": 5, "contaId": %d,
                 "cartaoId": %s, "dataInicio": "%s", "alcance": %s}
                """.formatted(valor, periodicidade, contaId, cartaoId, dataInicio, alcance == null ? "null" : "\"" + alcance + "\"");
    }

    private Long criarAssinatura(String token, Long contaId, Long cartaoId, String periodicidade, String dataInicio) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/assinaturas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, cartaoId, "30.00", periodicidade, dataInicio, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private Long criarCartao(String token) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Nubank\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\", \"diaFechamento\": 3, \"diaVencimento\": 10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private List<String> datasDasCobrancas(String token) throws Exception {
        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(lista, "$[?(@.assinaturaId != null)].dataTransacao");
    }

    @Test
    void mensalLancaAsCobrancasAteDozeMesesAFrenteESoAsPassadasContamNoSaldo() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        criarAssinatura(token, contaId, null, "MENSAL", "2026-01-10");

        List<String> datas = datasDasCobrancas(token);
        assertThat(datas).hasSize(15).contains("2026-01-10", "2027-03-10").doesNotContain("2027-04-10");

        mockMvc.perform(comToken(get("/api/assinaturas"), token))
                .andExpect(jsonPath("$[0].periodicidade").value("MENSAL"))
                .andExpect(jsonPath("$[0].proximaCobranca").value("2026-04-10"))
                .andExpect(jsonPath("$[0].canceladaEm").doesNotExist());

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalSaidas").value(90.00));
    }

    @Test
    void anualNoCartaoCaiNoVencimentoDaFatura() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        criarAssinatura(token, contaId, criarCartao(token), "ANUAL", "2026-03-20");

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataCompra")).containsExactly("2026-03-20");
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataTransacao")).containsExactly("2026-04-10");

        mockMvc.perform(comToken(get("/api/assinaturas"), token))
                .andExpect(jsonPath("$[0].proximaCobranca").value("2026-03-20"))
                .andExpect(jsonPath("$[0].nomeCartao").value("Nubank"));
    }

    @Test
    void cancelarApagaSoAsCobrancasQueAindaNaoForamFeitasEMantemNaLista() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long assinaturaId = criarAssinatura(token, contaId, criarCartao(token), "MENSAL", "2026-02-05");

        mockMvc.perform(comToken(post("/api/assinaturas/" + assinaturaId + "/cancelar"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladaEm").value("2026-03-15"))
                .andExpect(jsonPath("$.proximaCobranca").doesNotExist());

        // A compra de 05/03 já foi feita: fica, mesmo vencendo só em abril
        assertThat(datasDasCobrancas(token)).containsExactly("2026-04-10", "2026-03-10");

        mockMvc.perform(comToken(get("/api/assinaturas"), token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canceladaEm").value("2026-03-15"));

        mockMvc.perform(comToken(post("/api/assinaturas/" + assinaturaId + "/cancelar"), token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Esta assinatura já está cancelada"));
        mockMvc.perform(comToken(put("/api/assinaturas/" + assinaturaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, null, "10.00", "MENSAL", "2026-02-05", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Assinatura cancelada não pode ser editada"));
    }

    @Test
    void excluirApagaAAssinaturaETodasAsCobrancas() throws Exception {
        String token = cadastrarUsuario();
        Long assinaturaId = criarAssinatura(token, idContaPadrao(token), null, "MENSAL", "2026-01-10");

        mockMvc.perform(comToken(delete("/api/assinaturas/" + assinaturaId), token))
                .andExpect(status().isNoContent());

        assertThat(datasDasCobrancas(token)).isEmpty();
        mockMvc.perform(comToken(get("/api/assinaturas"), token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void editarSoAsFuturasMantemAsCobrancasAteHoje() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long assinaturaId = criarAssinatura(token, contaId, null, "MENSAL", "2026-01-10");

        mockMvc.perform(comToken(put("/api/assinaturas/" + assinaturaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, null, "45.00", "MENSAL", "2026-01-10", "FUTURAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(45.00));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
        List<Number> valores = JsonPath.read(lista, "$[*].valor");

        assertThat(valores).hasSize(15);
        assertThat(valores.subList(12, 15)).extracting(Number::doubleValue).containsOnly(30.0);
        assertThat(valores.subList(0, 12)).extracting(Number::doubleValue).containsOnly(45.0);
    }

    @Test
    void editarTodasRefazAsCobrancasDesdeONovoInicio() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long assinaturaId = criarAssinatura(token, contaId, null, "MENSAL", "2026-01-10");

        mockMvc.perform(comToken(put("/api/assinaturas/" + assinaturaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, null, "45.00", "ANUAL", "2025-06-01", "TODAS")))
                .andExpect(status().isOk());

        assertThat(datasDasCobrancas(token)).containsExactly("2026-06-01", "2025-06-01");
    }

    @Test
    void loginCompletaAsCobrancasSemDuplicar() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);
        criarAssinatura(token, idContaPadrao(token), null, "MENSAL", "2026-01-10");

        relogio.definir(Instant.parse("2026-06-15T15:00:00Z"));
        login(email, SENHA_PADRAO);
        String tokenNovo = login(email, SENHA_PADRAO);

        List<String> datas = datasDasCobrancas(tokenNovo);
        assertThat(datas).hasSize(18).doesNotHaveDuplicates().contains("2027-06-10");
    }

    @Test
    void validaCategoriaDeSaidaEInicioDeAteDezAnosAtras() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/assinaturas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, null, "30.00", "MENSAL", "2026-01-10", null).replace("\"categoriaId\": 5", "\"categoriaId\": 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A categoria da assinatura deve ser de saída"));

        mockMvc.perform(comToken(post("/api/assinaturas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(contaId, null, "30.00", "MENSAL", "2016-01-10", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data de início deve ser de no máximo 10 anos atrás"));

        mockMvc.perform(comToken(post("/api/assinaturas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"X\", \"valor\": 10, \"categoriaId\": 5, \"contaId\": %d}".formatted(contaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.periodicidade").value("A periodicidade (mensal ou anual) é obrigatória"));
    }

    @Test
    void excluirAContaApagaAsAssinaturasDela() throws Exception {
        String token = cadastrarUsuario();
        String conta = mockMvc.perform(comToken(post("/api/contas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Empresa\", \"corFundo\": \"#FFFFFF\", \"corTexto\": \"#000000\"}"))
                .andReturn().getResponse().getContentAsString();
        Long contaEmpresa = ((Number) JsonPath.read(conta, "$.id")).longValue();
        criarAssinatura(token, contaEmpresa, null, "MENSAL", "2026-01-10");

        mockMvc.perform(comToken(delete("/api/contas/" + contaEmpresa), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isNoContent());

        assertThat(datasDasCobrancas(token)).isEmpty();
        mockMvc.perform(comToken(get("/api/assinaturas"), token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void outroUsuarioNaoMexeNaAssinatura() throws Exception {
        String tokenDona = cadastrarUsuario();
        Long assinaturaId = criarAssinatura(tokenDona, idContaPadrao(tokenDona), null, "MENSAL", "2026-01-10");
        String tokenIntruso = cadastrarUsuario();

        mockMvc.perform(comToken(post("/api/assinaturas/" + assinaturaId + "/cancelar"), tokenIntruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(delete("/api/assinaturas/" + assinaturaId), tokenIntruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(put("/api/assinaturas/" + assinaturaId), tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(idContaPadrao(tokenIntruso), null, "1.00", "MENSAL", "2026-01-10", null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(get("/api/assinaturas"), tokenIntruso)).andExpect(jsonPath("$.length()").value(0));
    }
}
