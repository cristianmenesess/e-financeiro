package com.efinanceiro.seguranca;

import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O front trata "401 sem corpo" como sessão expirada e desloga. Token expirado, malformado ou com
 * assinatura adulterada precisa cair nesse caso (antes a exceção escapava do filtro).
 */
class AutenticacaoJwtTeste extends TesteIntegracao {

    @Test
    void tokenValidoAcessaRotaProtegida() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(get("/api/contas"), token))
                .andExpect(status().isOk());
    }

    @Test
    void semTokenDevolve401SemCorpo() throws Exception {
        mockMvc.perform(get("/api/contas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void tokenExpiradoDevolve401SemCorpo() throws Exception {
        String token = cadastrarUsuario();

        relogio.avancar(Duration.ofHours(25));

        mockMvc.perform(comToken(get("/api/contas"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void tokenMalformadoDevolve401SemCorpo() throws Exception {
        mockMvc.perform(comToken(get("/api/contas"), "isso.nao.e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/contas").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void tokenComAssinaturaAdulteradaDevolve401() throws Exception {
        String token = cadastrarUsuario();
        String adulterado = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");

        mockMvc.perform(comToken(get("/api/contas"), adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void tokenEmitidoAntesDaTrocaDeSenhaDeixaDeValer() throws Exception {
        String email = emailUnico();
        String tokenAntigo = cadastrarUsuario(email);

        mockMvc.perform(post("/api/autenticacao/esqueci-senha").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(servicoEmail).enviarEmailRedefinicaoSenha(eq(email), anyString(), link.capture());
        String tokenRedefinicao = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        relogio.avancar(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/autenticacao/redefinir-senha").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"novaSenha\": \"nova-senha-456\"}".formatted(tokenRedefinicao)))
                .andExpect(status().isOk());

        mockMvc.perform(comToken(get("/api/contas"), tokenAntigo))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        String resposta = mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"nova-senha-456\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String tokenNovo = JsonPath.read(resposta, "$.token");

        mockMvc.perform(comToken(get("/api/contas"), tokenNovo))
                .andExpect(status().isOk());
    }
}
