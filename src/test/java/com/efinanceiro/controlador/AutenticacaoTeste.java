package com.efinanceiro.controlador;

import com.efinanceiro.suporte.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacaoTeste extends TesteIntegracao {

    @Test
    void emailEGravadoMinusculoEOLoginIgnoraMaiusculas() throws Exception {
        String sufixo = emailUnico();
        String emailComMaiusculas = "Maria." + sufixo.toUpperCase().replace("@TESTE.COM", "@Teste.COM");

        mockMvc.perform(post("/api/autenticacao/cadastro").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Maria", "email": "%s", "senha": "%s"}
                                """.formatted(emailComMaiusculas, SENHA_PADRAO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(emailComMaiusculas.toLowerCase()));

        mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}
                                """.formatted(emailComMaiusculas.toUpperCase(), SENHA_PADRAO)))
                .andExpect(status().isOk());
    }

    @Test
    void cadastroDuplicadoSoPorMaiusculasDevolve409() throws Exception {
        String email = emailUnico();
        cadastrarUsuario(email);

        mockMvc.perform(post("/api/autenticacao/cadastro").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Outra pessoa", "email": "%s", "senha": "%s"}
                                """.formatted(email.toUpperCase(), SENHA_PADRAO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Já existe um usuário cadastrado com esse e-mail"));
    }

    @Test
    void senhaAcimaDe72BytesDevolve400EmVezDe500() throws Exception {
        // 40 caracteres acentuados = 80 bytes em UTF-8: passaria num @Size(max = 72) e quebraria o BCrypt
        String senhaLonga = "ç".repeat(40);

        mockMvc.perform(post("/api/autenticacao/cadastro").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Teste", "email": "%s", "senha": "%s"}
                                """.formatted(emailUnico(), senhaLonga)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.senha").value("A senha deve ter no máximo 72 caracteres"));
    }

    @Test
    void nomeAcimaDoLimiteDevolve400() throws Exception {
        mockMvc.perform(post("/api/autenticacao/cadastro").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s", "email": "%s", "senha": "%s"}
                                """.formatted("a".repeat(121), emailUnico(), SENHA_PADRAO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nome").value("O nome deve ter no máximo 120 caracteres"));
    }

    @Test
    void esqueciSenhaRespondeIgualComOuSemContaEMesmoComFalhaNoEnvio() throws Exception {
        String email = emailUnico();
        cadastrarUsuario(email);

        doThrow(new ResourceAccessException("Resend fora do ar"))
                .when(servicoEmail).enviarEmailRedefinicaoSenha(anyString(), anyString(), anyString());

        // E-mail cadastrado, com o envio falhando: antes dava 500 e entregava que a conta existe
        mockMvc.perform(post("/api/autenticacao/esqueci-senha").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/autenticacao/esqueci-senha").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(emailUnico())))
                .andExpect(status().isOk());
    }

    @Test
    void cadastroEmExcessoDoMesmoIpDevolve429() throws Exception {
        String ipFixo = "203.0.113.10";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/autenticacao/cadastro").with(ip(ipFixo))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome": "Robô", "email": "%s", "senha": "%s"}
                                    """.formatted(emailUnico(), SENHA_PADRAO)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/autenticacao/cadastro").with(ip(ipFixo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Robô", "email": "%s", "senha": "%s"}
                                """.formatted(emailUnico(), SENHA_PADRAO)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").value("Muitas tentativas. Aguarde alguns minutos e tente de novo."));
    }

    @Test
    void forcaBrutaNaSenhaDeUmaContaEBloqueadaMesmoTrocandoDeIp() throws Exception {
        String email = emailUnico();
        cadastrarUsuario(email);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\", \"senha\": \"chute-%d\"}".formatted(email, i)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA_PADRAO)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void esqueciSenhaEmExcessoParaOMesmoEmailNaoDisparaMaisEmails() throws Exception {
        String email = emailUnico();
        cadastrarUsuario(email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/autenticacao/esqueci-senha").with(ipAleatorio())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\"}".formatted(email)))
                    .andExpect(status().isOk());
        }

        clearInvocations(servicoEmail);

        mockMvc.perform(post("/api/autenticacao/esqueci-senha").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isTooManyRequests());

        verify(servicoEmail, never()).enviarEmailRedefinicaoSenha(anyString(), anyString(), anyString());
    }

    @Test
    void loginDevolveFotoUrlNulaParaQuemNaoTemFoto() throws Exception {
        String email = emailUnico();
        cadastrarUsuario(email);

        mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA_PADRAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.token").exists());
    }
}
