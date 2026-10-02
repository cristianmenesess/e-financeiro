package com.efinanceiro.controlador;

import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PerfilTeste extends TesteIntegracao {

    private static final byte[] JPEG_VALIDO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final String URL_FOTO = "https://res.cloudinary.com/demo/image/upload/v1/efinanceiro/usuarios/1.jpg";

    private String atualizarPerfil(String nome, String email, String senhaAtual) {
        return """
                {"nome": "%s", "email": "%s", "senhaAtual": %s}
                """.formatted(nome, email, senhaAtual == null ? "null" : "\"" + senhaAtual + "\"");
    }

    // ---------- GET / PUT dados ----------

    @Test
    void buscarPerfilDevolveNomeEmailEFoto() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);

        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Usuário de Teste"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fotoUrl").value(nullValue()))
                .andExpect(jsonPath("$.token").value(nullValue()));
    }

    @Test
    void atualizarSoONomeNaoPedeSenhaNemEmiteToken() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);

        mockMvc.perform(comToken(put("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("  Novo Nome  ", email.toUpperCase(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").value(nullValue()));

        // O token antigo continua valendo: nada de sessão derrubada por mudar só o nome
        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(jsonPath("$.nome").value("Novo Nome"));
    }

    @Test
    void trocarEmailExigeSenhaAtual() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(put("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailUnico(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Informe a senha atual para trocar o e-mail"));

        mockMvc.perform(comToken(put("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailUnico(), "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));
    }

    @Test
    void trocarParaEmailDeOutroUsuarioDevolve409() throws Exception {
        String emailOcupado = emailUnico();
        cadastrarUsuario(emailOcupado);
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(put("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailOcupado.toUpperCase(), SENHA_PADRAO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Já existe um usuário cadastrado com esse e-mail"));
    }

    @Test
    void trocarEmailDevolveTokenNovoEDerrubaOAntigo() throws Exception {
        String tokenAntigo = cadastrarUsuario();
        String emailNovo = emailUnico();

        String resposta = mockMvc.perform(comToken(put("/api/perfil"), tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailNovo, SENHA_PADRAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailNovo))
                .andReturn().getResponse().getContentAsString();

        String tokenNovo = JsonPath.read(resposta, "$.token");

        mockMvc.perform(comToken(get("/api/perfil"), tokenNovo))
                .andExpect(status().isOk());
        mockMvc.perform(comToken(get("/api/perfil"), tokenAntigo))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        login(emailNovo, SENHA_PADRAO);
    }

    @Test
    void tokenAntigoNaoEntraNaContaDeQuemCadastraOEmailLiberado() throws Exception {
        String emailA = emailUnico();
        String tokenA = cadastrarUsuario(emailA);

        relogio.avancar(Duration.ofSeconds(5));

        String emailB = emailUnico();
        mockMvc.perform(comToken(put("/api/perfil"), tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailB, SENHA_PADRAO)))
                .andExpect(status().isOk());

        // Alguém livre pra usar o e-mail A cadastra uma conta nova
        cadastrarUsuario(emailA);

        // O token antigo (emitido pro dono original de A) não pode logar no dono novo de A
        mockMvc.perform(comToken(get("/api/perfil"), tokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void tokenAntigoNaoVoltaAValerQuandoOEmailVoltaAoOriginal() throws Exception {
        String emailA = emailUnico();
        String tokenA = cadastrarUsuario(emailA);

        relogio.avancar(Duration.ofSeconds(5));

        String emailB = emailUnico();
        String resposta = mockMvc.perform(comToken(put("/api/perfil"), tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailB, SENHA_PADRAO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenB = JsonPath.read(resposta, "$.token");

        relogio.avancar(Duration.ofSeconds(5));

        mockMvc.perform(comToken(put("/api/perfil"), tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizarPerfil("Teste", emailA, SENHA_PADRAO)))
                .andExpect(status().isOk());

        // O token antigo (de antes da primeira troca) não deve ressuscitar
        mockMvc.perform(comToken(get("/api/perfil"), tokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    // ---------- senha ----------

    @Test
    void trocarSenhaComSenhaAtualErradaDevolve401() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(put("/api/perfil/senha"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"errada\", \"novaSenha\": \"nova-senha-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));
    }

    @Test
    void trocarSenhaDerrubaTokenAntigoEEntregaUmNovo() throws Exception {
        String email = emailUnico();
        String tokenAntigo = cadastrarUsuario(email);

        relogio.avancar(Duration.ofSeconds(5));

        String resposta = mockMvc.perform(comToken(put("/api/perfil/senha"), tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"%s\", \"novaSenha\": \"nova-senha-123\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String tokenNovo = JsonPath.read(resposta, "$.token");

        mockMvc.perform(comToken(get("/api/perfil"), tokenAntigo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(comToken(get("/api/perfil"), tokenNovo))
                .andExpect(status().isOk());

        login(email, "nova-senha-123");
    }

    @Test
    void seisTentativasDeSenhaAtualEmQuinzeMinutosDevolve429() throws Exception {
        String token = cadastrarUsuario();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(comToken(put("/api/perfil/senha"), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senhaAtual\": \"chute-%d\", \"novaSenha\": \"nova-senha-123\"}".formatted(i)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(comToken(put("/api/perfil/senha"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"%s\", \"novaSenha\": \"nova-senha-123\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isTooManyRequests());
    }

    // ---------- foto ----------

    @Test
    void enviarFotoGravaAUrlDevolvidaPeloCloudinary() throws Exception {
        String token = cadastrarUsuario();
        when(servicoFotoPerfil.enviar(anyLong(), any())).thenReturn(URL_FOTO);

        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG_VALIDO)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO));

        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO));
    }

    @Test
    void arquivoQueNaoEImagemAceitaDevolve400MesmoComContentTypeDeImagem() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "virus.jpg", "image/jpeg", "MZ-executavel".getBytes())), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A foto deve ser JPG, PNG ou WEBP"));

        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "vazia.jpg", "image/jpeg", new byte[0])), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Selecione uma foto"));

        verify(servicoFotoPerfil, never()).enviar(anyLong(), any());
    }

    @Test
    void fotoAcimaDe5MbDevolve400() throws Exception {
        String token = cadastrarUsuario();
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG_VALIDO, 0, grande, 0, JPEG_VALIDO.length);

        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "grande.jpg", "image/jpeg", grande)), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A foto deve ter no máximo 5 MB"));
    }

    @Test
    void cloudinaryForaDoArDevolve502EMantemAFotoAnterior() throws Exception {
        String token = cadastrarUsuario();
        when(servicoFotoPerfil.enviar(anyLong(), any())).thenReturn(URL_FOTO);
        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG_VALIDO)), token))
                .andExpect(status().isOk());

        when(servicoFotoPerfil.enviar(anyLong(), any()))
                .thenThrow(new ServicoExternoIndisponivelException("Não foi possível salvar a foto agora. Tente de novo."));

        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "outra.jpg", "image/jpeg", JPEG_VALIDO)), token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível salvar a foto agora. Tente de novo."));

        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(jsonPath("$.fotoUrl").value(URL_FOTO));
    }

    @Test
    void removerFotoApagaNoCloudinaryELimpaAUrl() throws Exception {
        String token = cadastrarUsuario();
        when(servicoFotoPerfil.enviar(anyLong(), any())).thenReturn(URL_FOTO);
        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG_VALIDO)), token))
                .andExpect(status().isOk());

        mockMvc.perform(comToken(delete("/api/perfil/foto"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(nullValue()));

        verify(servicoFotoPerfil).apagar(anyLong());
    }

    // ---------- exclusão do cadastro ----------

    @Test
    void excluirCadastroComSenhaErradaNaoApagaNada() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);
        Long usuarioId = jdbcTemplate.queryForObject("select id from usuarios where email = ?", Long.class, email);

        mockMvc.perform(comToken(delete("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));

        login(email, SENHA_PADRAO);

        assertThat(jdbcTemplate.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuarioId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuarioId)).isEqualTo(1);
    }

    @Test
    void excluirCadastroApagaTodosOsDadosDoUsuarioEPreservaOsDosOutros() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);
        Long contaId = idContaPadrao(token);
        Long usuarioId = jdbcTemplate.queryForObject("select id from usuarios where email = ?", Long.class, email);

        mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Pet\", \"tipo\": \"SAIDA\", \"icone\": \"dog\", \"tom\": \"warning\"}"))
                .andExpect(status().isCreated());

        String tokenVizinho = cadastrarUsuario();
        criarTransacao(tokenVizinho, idContaPadrao(tokenVizinho), "ENTRADA", "10.00", "2026-03-01", null);

        String cartao = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Cartão\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\", \"diaFechamento\": 3, \"diaVencimento\": 10}"))
                .andReturn().getResponse().getContentAsString();
        Long cartaoId = ((Number) JsonPath.read(cartao, "$.id")).longValue();
        criarTransacao(token, contaId, "SAIDA", "50.00", "2026-03-01", cartaoId);
        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100, "tipo": "SAIDA", "categoriaId": 4,
                                 "contaId": %d, "cartaoId": %d, "totalParcelas": 3}
                                """.formatted(contaId, cartaoId)))
                .andExpect(status().isCreated());
        mockMvc.perform(comToken(post("/api/assinaturas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Streaming", "valor": 30, "periodicidade": "MENSAL", "categoriaId": 5,
                                 "contaId": %d, "cartaoId": %d, "dataInicio": "2026-01-10"}
                                """.formatted(contaId, cartaoId)))
                .andExpect(status().isCreated());
        when(servicoFotoPerfil.enviar(anyLong(), any())).thenReturn(URL_FOTO);
        mockMvc.perform(comToken(multipart(HttpMethod.PUT, "/api/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", JPEG_VALIDO)), token))
                .andExpect(status().isOk());

        mockMvc.perform(comToken(delete("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isNoContent());

        for (String tabela : new String[]{"transacoes", "recorrencias", "assinaturas", "categorias", "cartoes", "contas"}) {
            Integer restantes = jdbcTemplate.queryForObject(
                    "select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuarioId);
            assertThat(restantes).as(tabela).isZero();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from categorias where usuario_id is null", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuarioId)).isZero();

        verify(servicoFotoPerfil).apagar(usuarioId);

        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(comToken(get("/api/transacoes"), tokenVizinho))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
