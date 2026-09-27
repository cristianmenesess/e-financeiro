package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransacaoTeste extends TesteIntegracao {

    private Long criarConta(String token, String nome) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/contas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"%s\", \"corFundo\": \"#FFFFFF\", \"corTexto\": \"#000000\"}".formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    @Test
    void resumoSomadoNoBancoBateComOsLancamentosEFiltraPorConta() throws Exception {
        String token = cadastrarUsuario();
        Long pessoal = idContaPadrao(token);
        Long empresa = criarConta(token, "Empresa");

        criarTransacao(token, pessoal, "ENTRADA", "3000.50", "2026-03-01", null);
        criarTransacao(token, pessoal, "SAIDA", "1200.25", "2026-03-10", null);
        criarTransacao(token, empresa, "ENTRADA", "5000.00", "2026-03-05", null);
        criarTransacao(token, empresa, "SAIDA", "999.99", "2026-04-10", null); // futura: não conta

        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.totalEntradas").value(8000.50))
                .andExpect(jsonPath("$.totalSaidas").value(1200.25))
                .andExpect(jsonPath("$.saldo").value(6800.25));

        mockMvc.perform(comToken(get("/api/transacoes/resumo").param("contaId", empresa.toString()), token))
                .andExpect(jsonPath("$.totalEntradas").value(5000.00))
                .andExpect(jsonPath("$.totalSaidas").value(0))
                .andExpect(jsonPath("$.saldo").value(5000.00));
    }

    @Test
    void listaSemPaginacaoContinuaDevolvendoTudoNoMesmoFormato() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        criarTransacao(token, contaId, "SAIDA", "10.00", "2026-03-01", null);
        criarTransacao(token, contaId, "SAIDA", "20.00", "2026-03-02", null);
        criarTransacao(token, contaId, "SAIDA", "30.00", "2026-03-03", null);

        mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].dataTransacao").value("2026-03-03"))
                .andExpect(jsonPath("$[0].nomeConta").value("Pessoal"))
                .andExpect(jsonPath("$[0].contaId").value(contaId))
                .andExpect(jsonPath("$[0].categoriaId").value(5))
                .andExpect(jsonPath("$[0].nomeCategoria").value("Outro"))
                .andExpect(jsonPath("$[0].recorrenciaId").doesNotExist());
    }

    @Test
    void listaPaginadaDevolvePaginaETotalNoCabecalho() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        criarTransacao(token, contaId, "SAIDA", "10.00", "2026-03-01", null);
        criarTransacao(token, contaId, "SAIDA", "20.00", "2026-03-02", null);
        criarTransacao(token, contaId, "SAIDA", "30.00", "2026-03-03", null);

        String pagina2 = mockMvc.perform(comToken(get("/api/transacoes").param("pagina", "1").param("tamanho", "2"), token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andReturn().getResponse().getContentAsString();

        List<String> datas = JsonPath.read(pagina2, "$[*].dataTransacao");
        assertThat(datas).containsExactly("2026-03-01");

        mockMvc.perform(comToken(get("/api/transacoes").param("tamanho", "500"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O tamanho da página deve ser no máximo 200"));
    }

    @Test
    void descricaoLongaEValorForaDoLimiteDevolvem400() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "%s", "valor": 10, "tipo": "SAIDA", "contaId": %d, "categoriaId": 5}
                                """.formatted("x".repeat(161), contaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.descricao").value("A descrição deve ter no máximo 160 caracteres"));

        for (String valorInvalido : List.of("12345678901.00", "10.001")) {
            mockMvc.perform(comToken(post("/api/transacoes"), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"descricao": "X", "valor": %s, "tipo": "SAIDA", "contaId": %d, "categoriaId": 5}
                                    """.formatted(valorInvalido, contaId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valor").value("O valor deve ter no máximo 10 dígitos inteiros e 2 casas decimais"));
        }
    }

    @Test
    void gastoDoMesDeCadaCartaoSaiDaConsultaAgregada() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        Long cartaoA = criarCartao(token, "Cartão A");
        Long cartaoB = criarCartao(token, "Cartão B");
        criarCartao(token, "Cartão sem gasto");

        criarTransacao(token, contaId, "SAIDA", "50.00", "2026-03-02", cartaoA);
        criarTransacao(token, contaId, "SAIDA", "25.50", "2026-03-14", cartaoA);
        criarTransacao(token, contaId, "SAIDA", "999.00", "2026-03-20", cartaoA);  // ainda não aconteceu
        criarTransacao(token, contaId, "SAIDA", "70.00", "2026-02-28", cartaoB);   // mês passado
        criarTransacao(token, contaId, "ENTRADA", "40.00", "2026-03-10", cartaoB); // estorno não é gasto

        mockMvc.perform(comToken(get("/api/cartoes"), token))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão A')].gastoNoMes").value(75.50))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão B')].gastoNoMes").value(0))
                .andExpect(jsonPath("$[?(@.nome == 'Cartão sem gasto')].gastoNoMes").value(0));
    }

    private Long criarCartao(String token, String nome) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"%s\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\"}".formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }
}
