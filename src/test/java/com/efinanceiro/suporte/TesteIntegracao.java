package com.efinanceiro.suporte;

import com.efinanceiro.servico.ServicoEmail;
import com.efinanceiro.servico.ServicoFotoPerfil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: sobe a aplicação inteira contra um Postgres real
 * (Testcontainers), com relógio controlado e envio de e-mail mockado. Cada teste cria os
 * próprios usuários (e-mails únicos), então os testes não dependem uns dos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
@Import(ConfiguracaoTestes.class)
public abstract class TesteIntegracao {

    /** 15/03/2026 às 12h00 em Brasília (15h00 UTC). */
    public static final Instant INSTANTE_PADRAO = Instant.parse("2026-03-15T15:00:00Z");

    public static final String SENHA_PADRAO = "senha-segura-123";

    /** Ids das categorias fixas: a V8 sempre as insere nessa ordem num banco novo. */
    public static final long CATEGORIA_RENDA = 1L;
    public static final long CATEGORIA_DESPESA = 2L;
    public static final long CATEGORIA_ALIMENTACAO = 3L;
    public static final long CATEGORIA_MORADIA = 4L;
    public static final long CATEGORIA_OUTRO = 5L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RelogioDeTeste relogio;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected ServicoEmail servicoEmail;

    @MockitoBean
    protected ServicoFotoPerfil servicoFotoPerfil;

    @BeforeEach
    void reiniciarRelogio() {
        relogio.definir(INSTANTE_PADRAO);
    }

    /**
     * Gera um e-mail que nenhum outro teste usa.
     */
    protected String emailUnico() {
        return "usuario-" + UUID.randomUUID() + "@teste.com";
    }

    /**
     * Simula a requisição vindo de um IP aleatório, pra que o limite de tentativas por IP de um
     * teste não interfira nos outros (todos rodam no mesmo contexto).
     */
    protected RequestPostProcessor ipAleatorio() {
        String ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256)
                + "." + ThreadLocalRandom.current().nextInt(1, 255);
        return ip(ip);
    }

    protected RequestPostProcessor ip(String ip) {
        return requisicao -> {
            requisicao.setRemoteAddr(ip);
            return requisicao;
        };
    }

    /**
     * Cadastra um usuário novo e devolve o token JWT dele.
     */
    protected String cadastrarUsuario(String email) throws Exception {
        String resposta = mockMvc.perform(post("/api/autenticacao/cadastro").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Usuário de Teste", "email": "%s", "senha": "%s"}
                                """.formatted(email, SENHA_PADRAO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(resposta, "$.token");
    }

    protected String cadastrarUsuario() throws Exception {
        return cadastrarUsuario(emailUnico());
    }

    /**
     * Faz login e devolve o token (falha o teste se o login não der 200).
     */
    protected String login(String email, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/api/autenticacao/login").with(ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(resposta, "$.token");
    }

    /**
     * Id da conta "Pessoal" criada automaticamente no cadastro.
     */
    protected Long idContaPadrao(String token) throws Exception {
        String resposta = mockMvc.perform(comToken(get("/api/contas"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$[0].id")).longValue();
    }

    /**
     * Cria uma transação e devolve o id dela. Data nula = backend usa "hoje".
     */
    protected Long criarTransacao(String token, Long contaId, String tipo, String valor, String data, Long cartaoId) throws Exception {
        long categoriaId = "ENTRADA".equals(tipo) ? CATEGORIA_RENDA : CATEGORIA_OUTRO;
        String corpo = """
                {"descricao": "Lançamento", "valor": %s, "tipo": "%s", "contaId": %d, "categoriaId": %d,
                 "cartaoId": %s, "dataTransacao": %s}
                """.formatted(valor, tipo, contaId, categoriaId, cartaoId, data == null ? "null" : "\"" + data + "\"");

        String resposta = mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B comToken(B requisicao, String token) {
        return requisicao.header("Authorization", "Bearer " + token);
    }
}
