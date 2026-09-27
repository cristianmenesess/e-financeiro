package com.efinanceiro.excecao;

import com.efinanceiro.suporte.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Erros causados pelo cliente caíam no handler genérico e voltavam 500 ("Erro interno"); cada caso
 * abaixo precisa voltar o status 4xx certo, com o corpo padrão { mensagem, timestamp }.
 */
class TratadorGlobalDeExcecoesTeste extends TesteIntegracao {

    @Test
    void jsonMalformadoDevolve400() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ isso não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisição inválido ou ausente"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void enumInexistenteDevolve400ApontandoOCampo() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "X", "valor": 10, "tipo": "XPTO", "contaId": %d, "categoriaId": 5}
                                """.formatted(contaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para o campo 'tipo'"));
    }

    @Test
    void dataEmFormatoInvalidoDevolve400() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "X", "valor": 10, "tipo": "SAIDA", "contaId": %d, "categoriaId": 5,
                                 "dataTransacao": "31/12/2026"}
                                """.formatted(contaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para o campo 'dataTransacao'"));
    }

    @Test
    void parametroComTipoErradoDevolve400() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(get("/api/transacoes").param("contaId", "abc"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para o parâmetro 'contaId'"));

        mockMvc.perform(comToken(delete("/api/cartoes/abc"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exclusaoDeContaSemCorpoDevolve400() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(delete("/api/contas/" + contaId), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void rotaInexistenteDevolve404() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(get("/api/rota-que-nao-existe"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Rota não encontrada"));
    }

    @Test
    void metodoHttpErradoDevolve405ComCabecalhoAllow() throws Exception {
        mockMvc.perform(get("/api/autenticacao/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.mensagem").value("Método GET não é permitido nesta rota"));
    }

    @Test
    void corpoSemContentTypeJsonDevolve415() throws Exception {
        mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=a@b.com"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
