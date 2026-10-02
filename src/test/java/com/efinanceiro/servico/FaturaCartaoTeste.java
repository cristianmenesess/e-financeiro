package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relógio de teste em 15/03/2026.
 */
class FaturaCartaoTeste extends TesteIntegracao {

    private Long criarCartao(String token, String nome, int diaFechamento, int diaVencimento) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "corFundo": "#111111", "corTexto": "#FFFFFF", "diaFechamento": %d, "diaVencimento": %d}
                                """.formatted(nome, diaFechamento, diaVencimento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private Long criarCompraParcelada(String token, Long contaId, Long cartaoId, int parcelas, String dataCompra) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "TV", "valor": 100, "tipo": "SAIDA", "categoriaId": 5, "contaId": %d,
                                 "cartaoId": %d, "totalParcelas": %d, "dataInicio": "%s"}
                                """.formatted(contaId, cartaoId, parcelas, dataCompra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private String listarTransacoes(String token) throws Exception {
        return mockMvc.perform(comToken(get("/api/transacoes"), token)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void faturaAtualDeCadaCartaoSegueOProprioCiclo() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        Long cartaoA = criarCartao(token, "Cartão A", 3, 10);
        Long cartaoB = criarCartao(token, "Cartão B", 25, 5);
        criarCartao(token, "Cartão sem gasto", 10, 20);

        criarTransacao(token, contaId, "SAIDA", "999.00", "2026-03-02", cartaoA);  // fatura que venceu em 10/03
        criarTransacao(token, contaId, "SAIDA", "50.00", "2026-03-03", cartaoA);
        criarTransacao(token, contaId, "SAIDA", "25.50", "2026-03-14", cartaoA);
        criarTransacao(token, contaId, "SAIDA", "999.00", "2026-04-04", cartaoA);  // fatura seguinte
        criarTransacao(token, contaId, "SAIDA", "70.00", "2026-02-26", cartaoB);
        criarTransacao(token, contaId, "SAIDA", "999.00", "2026-03-25", cartaoB);  // fatura seguinte
        criarTransacao(token, contaId, "ENTRADA", "40.00", "2026-03-10", cartaoB); // estorno não é gasto

        mockMvc.perform(comToken(get("/api/cartoes"), token))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão A')].faturaAtual").value(75.50))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão A')].vencimentoFaturaAtual").value("2026-04-10"))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão B')].faturaAtual").value(70.00))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão B')].vencimentoFaturaAtual").value("2026-04-05"))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão sem gasto')].faturaAtual").value(0))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão sem gasto')].vencimentoFaturaAtual").value("2026-04-20"))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão sem gasto')].diaFechamento").value(10))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão sem gasto')].diaVencimento").value(20));
    }

    @Test
    void compraNoCartaoCaiNoVencimentoDaFaturaESoContaNoSaldoQuandoVence() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long cartaoId = criarCartao(token, "Nubank", 3, 10);

        criarTransacao(token, contaId, "SAIDA", "80.00", "2026-03-14", cartaoId);
        criarTransacao(token, contaId, "SAIDA", "20.00", "2026-03-14", null);

        String lista = listarTransacoes(token);
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.valor == 80.0)].dataTransacao")).containsExactly("2026-04-10");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.valor == 80.0)].dataCompra")).containsExactly("2026-03-14");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.valor == 20.0)].dataTransacao")).containsExactly("2026-03-14");
        assertThat((List<Object>) JsonPath.read(lista, "$[?(@.valor == 20.0)].dataCompra")).containsExactly((Object) null);

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalSaidas").value(20.00));
    }

    @Test
    void parcelasNoCartaoVencemUmaEmCadaFaturaMesmoDepoisDeEditadas() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long cartaoId = criarCartao(token, "Nubank", 3, 10);

        criarCompraParcelada(token, contaId, cartaoId, 3, "2026-03-14");

        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$[0].dataInicio").value("2026-03-14"))
                .andExpect(jsonPath("$[0].parcelasRestantes").value(3));

        String lista = listarTransacoes(token);
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataTransacao")).containsExactly("2026-06-10", "2026-05-10", "2026-04-10");
        assertThat((List<String>) JsonPath.read(lista, "$[*].dataCompra")).containsOnly("2026-03-14");

        Number segundaParcela = ((List<Number>) JsonPath.read(lista, "$[?(@.numeroParcela == 2)].id")).get(0);

        mockMvc.perform(comToken(put("/api/transacoes/" + segundaParcela), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "TV", "valor": 100, "tipo": "SAIDA", "contaId": %d, "categoriaId": 5,
                                 "cartaoId": %d, "dataTransacao": "2026-03-02"}
                                """.formatted(contaId, cartaoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroParcela").value(2))
                .andExpect(jsonPath("$.dataCompra").value("2026-03-02"))
                .andExpect(jsonPath("$.dataTransacao").value("2026-04-10"));
    }

    @Test
    void mudarOCicloDoCartaoRecalculaSoAsFaturasFuturas() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long cartaoId = criarCartao(token, "Nubank", 3, 10);

        criarTransacao(token, contaId, "SAIDA", "30.00", "2026-03-02", cartaoId);
        criarCompraParcelada(token, contaId, cartaoId, 2, "2026-03-14");

        mockMvc.perform(comToken(put("/api/cartoes/" + cartaoId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Nubank", "corFundo": "#111111", "corTexto": "#FFFFFF", "diaFechamento": 20, "diaVencimento": 27}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaFechamento").value(20))
                .andExpect(jsonPath("$.vencimentoFaturaAtual").value("2026-03-27"));

        String lista = listarTransacoes(token);
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.valor == 30.0)].dataTransacao")).containsExactly("2026-03-10");
        assertThat((List<String>) JsonPath.read(lista, "$[?(@.valor == 100.0)].dataTransacao")).containsExactly("2026-04-27", "2026-03-27");
    }

    @Test
    void cartaoExigeFechamentoEVencimentoEntreUmETrintaEUm() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Nubank\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.diaFechamento").value("O dia de fechamento da fatura é obrigatório"))
                .andExpect(jsonPath("$.diaVencimento").value("O dia de vencimento da fatura é obrigatório"));

        mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Nubank", "corFundo": "#111111", "corTexto": "#FFFFFF", "diaFechamento": 0, "diaVencimento": 32}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.diaFechamento").value("O dia de fechamento deve ser entre 1 e 31"))
                .andExpect(jsonPath("$.diaVencimento").value("O dia de vencimento deve ser entre 1 e 31"));
    }
}
