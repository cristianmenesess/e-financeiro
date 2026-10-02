package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Hoje" (15/03/2026 no relógio de teste) é o dia da 1ª parcela. A regra é uma só em todo lugar:
 * a parcela de hoje já ocorreu (conta no saldo) e "futuras" são só as de depois de hoje.
 */
class RecorrenciaTeste extends TesteIntegracao {

    private Long criarRecorrenciaComecandoHoje(String token, Long contaId) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100.00, "tipo": "SAIDA", "categoriaId": 4,
                                 "contaId": %d, "totalParcelas": 3, "dataInicio": "2026-03-15"}
                                """.formatted(contaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelasRestantes").value(2))
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    @Test
    void parcelaDeHojeContaNoSaldoENaoEFutura() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        criarRecorrenciaComecandoHoje(token, contaId);

        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$[0].parcelasRestantes").value(2));

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalSaidas").value(100.00))
                .andExpect(jsonPath("$.saldo").value(-100.00));
    }

    @Test
    void editarValorNaoMexeNaParcelaDeHojeQueJaContouNoSaldo() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long recorrenciaId = criarRecorrenciaComecandoHoje(token, contaId);

        mockMvc.perform(comToken(put("/api/recorrencias/" + recorrenciaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 250.00, "tipo": "SAIDA", "categoriaId": 4,
                                 "contaId": %d, "totalParcelas": 3, "dataInicio": "2026-03-15", "alcance": "FUTURAS"}
                                """.formatted(contaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(250.00))
                .andExpect(jsonPath("$.parcelasRestantes").value(2));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andReturn().getResponse().getContentAsString();

        List<Number> valoresEmOrdemDeData = JsonPath.read(lista, "$[*].valor");
        List<String> datas = JsonPath.read(lista, "$[*].dataTransacao");

        assertThat(datas).containsExactly("2026-05-15", "2026-04-15", "2026-03-15");
        assertThat(valoresEmOrdemDeData).extracting(Number::doubleValue).containsExactly(250.0, 250.0, 100.0);

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalSaidas").value(100.00));
    }

    @Test
    void excluirRecorrenciaApagaTodasAsParcelasInclusiveAsPassadas() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long recorrenciaId = criarRecorrenciaComecandoHoje(token, contaId);

        mockMvc.perform(comToken(delete("/api/recorrencias/" + recorrenciaId), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.saldo").value(0));
    }

    @Test
    void numeroDaParcelaNaoMudaQuandoADataDaParcelaEEditada() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        criarRecorrenciaComecandoHoje(token, contaId);

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andReturn().getResponse().getContentAsString();
        List<Number> idsDaSegundaParcela = JsonPath.read(lista, "$[?(@.dataTransacao == '2026-04-15')].id");
        Long idSegundaParcela = idsDaSegundaParcela.get(0).longValue();

        // Antes o número era calculado pela data: mover a 2ª parcela pra março virava "parcela 1"
        mockMvc.perform(comToken(put("/api/transacoes/" + idSegundaParcela), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100.00, "tipo": "SAIDA", "categoriaId": 4,
                                 "contaId": %d, "dataTransacao": "2026-03-20"}
                                """.formatted(contaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroParcela").value(2))
                .andExpect(jsonPath("$.totalParcelas").value(3));
    }

    @Test
    void parcelaComecandoNoDia31RecebeNumeroSequencial() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Assinatura", "valor": 30.00, "tipo": "SAIDA", "categoriaId": 5,
                                 "contaId": %d, "totalParcelas": 2, "dataInicio": "2026-01-31"}
                                """.formatted(contaId)))
                .andExpect(status().isCreated());

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andReturn().getResponse().getContentAsString();

        List<String> datas = JsonPath.read(lista, "$[*].dataTransacao");
        List<Integer> numeros = JsonPath.read(lista, "$[*].numeroParcela");

        assertThat(datas).containsExactly("2026-02-28", "2026-01-31");
        assertThat(numeros).containsExactly(2, 1);
    }

    private String edicao(Long contaId, String valor, int totalParcelas, String dataInicio, String alcance) {
        return """
                {"descricao": "Curso", "valor": %s, "tipo": "SAIDA", "categoriaId": 5, "contaId": %d,
                 "totalParcelas": %d, "dataInicio": "%s", "alcance": "%s"}
                """.formatted(valor, contaId, totalParcelas, dataInicio, alcance);
    }

    @Test
    void editarTodasRefazAsParcelasComOsDadosNovos() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long recorrenciaId = criarRecorrenciaComecandoHoje(token, contaId);

        mockMvc.perform(comToken(put("/api/recorrencias/" + recorrenciaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicao(contaId, "80.00", 4, "2026-01-15", "TODAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Curso"))
                .andExpect(jsonPath("$.dataInicio").value("2026-01-15"))
                .andExpect(jsonPath("$.parcelasRestantes").value(1));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(lista, "$[*].dataTransacao")).containsExactly("2026-04-15", "2026-03-15", "2026-02-15", "2026-01-15");
        assertThat((List<Integer>) JsonPath.read(lista, "$[*].numeroParcela")).containsExactly(4, 3, 2, 1);
        assertThat((List<String>) JsonPath.read(lista, "$[*].descricao")).containsOnly("Curso");
        assertThat((List<Number>) JsonPath.read(lista, "$[*].valor")).extracting(Number::doubleValue).containsOnly(80.0);
    }

    @Test
    void editarSoAsFuturasContinuaANumeracaoENaoDeixaOTotalMenorQueAsMantidas() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        String resposta = mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Curso", "valor": 50, "tipo": "SAIDA", "categoriaId": 5,
                                 "contaId": %d, "totalParcelas": 3, "dataInicio": "2026-01-15"}
                                """.formatted(contaId)))
                .andReturn().getResponse().getContentAsString();
        Long recorrenciaId = ((Number) JsonPath.read(resposta, "$.id")).longValue();

        mockMvc.perform(comToken(put("/api/recorrencias/" + recorrenciaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicao(contaId, "70.00", 2, "2026-01-15", "FUTURAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Já passaram 3 parcelas; o total não pode ser menor que isso"));

        mockMvc.perform(comToken(put("/api/recorrencias/" + recorrenciaId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicao(contaId, "70.00", 5, "2026-01-15", "FUTURAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalParcelas").value(5))
                .andExpect(jsonPath("$.parcelasRestantes").value(2));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();

        assertThat((List<String>) JsonPath.read(lista, "$[*].dataTransacao"))
                .containsExactly("2026-05-15", "2026-04-15", "2026-03-15", "2026-02-15", "2026-01-15");
        assertThat((List<Integer>) JsonPath.read(lista, "$[*].numeroParcela")).containsExactly(5, 4, 3, 2, 1);
        assertThat((List<Number>) JsonPath.read(lista, "$[*].valor")).extracting(Number::doubleValue).containsExactly(70.0, 70.0, 50.0, 50.0, 50.0);
    }
}
