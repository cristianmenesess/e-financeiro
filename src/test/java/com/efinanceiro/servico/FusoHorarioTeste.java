package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 15/03/2026 às 22h30 em Brasília = 16/03/2026 às 01h30 em UTC (fuso do servidor no Render).
 * Pro usuário ainda é dia 15: nenhuma regra de "hoje" pode usar o dia 16.
 */
class FusoHorarioTeste extends TesteIntegracao {

    private static final Instant NOITE_EM_BRASILIA = Instant.parse("2026-03-16T01:30:00Z");

    @BeforeEach
    void irParaANoite() {
        relogio.definir(NOITE_EM_BRASILIA);
    }

    @Test
    void transacaoSemDataUsaODiaDeBrasiliaENaoODeUtc() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Jantar", "valor": 80, "tipo": "SAIDA", "contaId": %d, "categoriaId": 3}
                                """.formatted(contaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataTransacao").value("2026-03-15"));
    }

    @Test
    void saldoNaoContaParcelaDeAmanhaDepoisDas21h() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        criarTransacao(token, contaId, "ENTRADA", "1000.00", "2026-03-15", null);
        criarTransacao(token, contaId, "SAIDA", "300.00", "2026-03-16", null);

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalEntradas").value(1000.00))
                .andExpect(jsonPath("$.totalSaidas").value(0))
                .andExpect(jsonPath("$.saldo").value(1000.00));
    }

    @Test
    void faturaDoCartaoNaoViraAntesDaHora() throws Exception {
        relogio.definir(Instant.parse("2026-04-01T01:30:00Z")); // 31/03 às 22h30 em Brasília

        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        String cartao = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Nubank\", \"corFundo\": \"#820AD1\", \"corTexto\": \"#FFFFFF\", \"diaFechamento\": 1, \"diaVencimento\": 10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cartaoId = ((Number) JsonPath.read(cartao, "$.id")).longValue();

        criarTransacao(token, contaId, "SAIDA", "120.00", "2026-03-31", cartaoId);

        mockMvc.perform(comToken(get("/api/cartoes"), token))
                .andExpect(jsonPath("$[0].faturaAtual").value(120.00))
                .andExpect(jsonPath("$[0].vencimentoFaturaAtual").value("2026-04-10"));
    }

    @Test
    void recorrenciaSemDataDeInicioComecaNoDiaDeBrasilia() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Academia", "valor": 90, "tipo": "SAIDA", "categoriaId": 5,
                                 "contaId": %d, "totalParcelas": 2}
                                """.formatted(contaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataInicio").value("2026-03-15"))
                .andExpect(jsonPath("$.parcelasRestantes").value(1));
    }
}
