package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um usuário nunca pode ver, alterar, excluir ou usar recursos de outro — a resposta é 404
 * (não 403), pra nem confirmar que o id existe.
 */
class PosseDeRecursosTeste extends TesteIntegracao {

    @Test
    void usuarioNaoMexeEmRecursosDeOutroUsuario() throws Exception {
        String tokenDona = cadastrarUsuario();
        String tokenIntruso = cadastrarUsuario();
        Long contaDona = idContaPadrao(tokenDona);
        Long transacaoDona = criarTransacao(tokenDona, contaDona, "SAIDA", "10.00", "2026-03-01", null);

        String cartao = mockMvc.perform(comToken(post("/api/cartoes"), tokenDona)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Cartão\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\", \"diaFechamento\": 3, \"diaVencimento\": 10}"))
                .andReturn().getResponse().getContentAsString();
        Long cartaoDona = ((Number) JsonPath.read(cartao, "$.id")).longValue();

        String recorrencia = mockMvc.perform(comToken(post("/api/recorrencias"), tokenDona)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100, "tipo": "SAIDA", "categoriaId": 4,
                                 "contaId": %d, "totalParcelas": 2}
                                """.formatted(contaDona)))
                .andReturn().getResponse().getContentAsString();
        Long recorrenciaDona = ((Number) JsonPath.read(recorrencia, "$.id")).longValue();

        String transacaoValida = """
                {"descricao": "X", "valor": 1, "tipo": "SAIDA", "contaId": %d, "categoriaId": 5}
                """;

        mockMvc.perform(comToken(delete("/api/transacoes/" + transacaoDona), tokenIntruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(put("/api/transacoes/" + transacaoDona), tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoValida.formatted(idContaPadrao(tokenIntruso))))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(post("/api/transacoes"), tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoValida.formatted(contaDona)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Conta não encontrada"));
        mockMvc.perform(comToken(delete("/api/cartoes/" + cartaoDona), tokenIntruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(delete("/api/recorrencias/" + recorrenciaDona), tokenIntruso))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(put("/api/recorrencias/" + recorrenciaDona), tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "X", "valor": 1, "tipo": "SAIDA", "categoriaId": 5, "contaId": %d, "totalParcelas": 1}
                                """.formatted(idContaPadrao(tokenIntruso))))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(get("/api/transacoes/resumo").param("contaId", contaDona.toString()), tokenIntruso))
                .andExpect(jsonPath("$.saldo").value(0));

        mockMvc.perform(comToken(get("/api/transacoes"), tokenDona))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(comToken(get("/api/cartoes"), tokenDona))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void naoPermiteExcluirAUnicaConta() throws Exception {
        String token = cadastrarUsuario();
        Long contaPadrao = idContaPadrao(token);

        mockMvc.perform(comToken(delete("/api/contas/" + contaPadrao), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Não é possível excluir a única conta. Crie outra conta antes de excluir esta."));
    }

    @Test
    void excluirContaExigeSenhaCorretaEApagaTransacoesERecorrenciasDela() throws Exception {
        String token = cadastrarUsuario();
        Long contaPadrao = idContaPadrao(token);

        String nova = mockMvc.perform(comToken(post("/api/contas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Empresa\", \"corFundo\": \"#FFFFFF\", \"corTexto\": \"#000000\"}"))
                .andReturn().getResponse().getContentAsString();
        Long contaEmpresa = ((Number) JsonPath.read(nova, "$.id")).longValue();

        criarTransacao(token, contaEmpresa, "ENTRADA", "500.00", "2026-03-01", null);
        criarTransacao(token, contaPadrao, "ENTRADA", "100.00", "2026-03-01", null);
        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Pró-labore", "valor": 800, "tipo": "ENTRADA", "categoriaId": 1,
                                 "contaId": %d, "totalParcelas": 3}
                                """.formatted(contaEmpresa)))
                .andExpect(status().isCreated());

        // 401 com mensagem = erro de domínio (o front só desloga em 401 sem mensagem)
        mockMvc.perform(comToken(delete("/api/contas/" + contaEmpresa), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"senha-errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));

        mockMvc.perform(comToken(delete("/api/contas/" + contaEmpresa), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isNoContent());

        mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andExpect(jsonPath("$.saldo").value(100.00));
    }
}
