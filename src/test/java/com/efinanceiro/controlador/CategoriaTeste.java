package com.efinanceiro.controlador;

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

class CategoriaTeste extends TesteIntegracao {

    private String corpo(String nome, String tipo, String icone, String tom) {
        return """
                {"nome": "%s", "tipo": "%s", "icone": "%s", "tom": "%s"}
                """.formatted(nome, tipo, icone, tom);
    }

    private Long criarCategoria(String token, String nome, String tipo) throws Exception {
        String resposta = mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(nome, tipo, "car", "brand")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    @Test
    void fixasVemPrimeiroComOsIdsEsperados() throws Exception {
        String token = cadastrarUsuario();
        criarCategoria(token, "Assinaturas", "SAIDA");

        String resposta = mockMvc.perform(comToken(get("/api/categorias"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(resposta, "$[0:5].id");
        List<String> nomes = JsonPath.read(resposta, "$[*].nome");
        List<Boolean> fixas = JsonPath.read(resposta, "$[*].fixa");

        // Guarda das constantes CATEGORIA_* usadas em todos os testes
        assertThat(ids).containsExactly((int) CATEGORIA_RENDA, (int) CATEGORIA_DESPESA, (int) CATEGORIA_ALIMENTACAO,
                (int) CATEGORIA_MORADIA, (int) CATEGORIA_OUTRO);
        assertThat(nomes).containsExactly("Renda", "Despesa", "Alimentação", "Moradia", "Outro", "Assinaturas");
        assertThat(fixas).containsExactly(true, true, true, true, true, false);
    }

    @Test
    void usuarioNaoVeCategoriaDeOutroUsuario() throws Exception {
        String tokenDona = cadastrarUsuario();
        criarCategoria(tokenDona, "Pet", "SAIDA");
        String tokenOutro = cadastrarUsuario();

        mockMvc.perform(comToken(get("/api/categorias"), tokenOutro))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void opcoesTrazemIconesETons() throws Exception {
        String token = cadastrarUsuario();

        String resposta = mockMvc.perform(comToken(get("/api/categorias/opcoes"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> icones = JsonPath.read(resposta, "$.icones");
        List<String> tons = JsonPath.read(resposta, "$.tons");

        assertThat(icones).contains("car", "dog", "banknote").hasSize(33);
        assertThat(tons).containsExactly("brand", "positive", "negative", "warning", "ai", "neutral");
    }

    @Test
    void nomeRepetidoComFixaOuComOutraDoUsuarioDevolve409() throws Exception {
        String token = cadastrarUsuario();
        criarCategoria(token, "Pet", "SAIDA");

        for (String nome : List.of("moradia", "PET", "  pet  ")) {
            mockMvc.perform(comToken(post("/api/categorias"), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(nome, "SAIDA", "dog", "warning")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensagem").value("Já existe uma categoria com esse nome"));
        }
    }

    @Test
    void iconeOuTomForaDaListaDevolve400() throws Exception {
        String token = cadastrarUsuario();

        mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Pet", "SAIDA", "icone-que-nao-existe", "brand")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Ícone inválido"));

        mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Pet", "SAIDA", "dog", "#FF0000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Cor inválida"));
    }

    @Test
    void quinquagesimaPrimeiraCategoriaDevolve422() throws Exception {
        String token = cadastrarUsuario();

        for (int i = 1; i <= 50; i++) {
            criarCategoria(token, "Categoria " + i, "SAIDA");
        }

        mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Categoria 51", "SAIDA", "car", "brand")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Limite de 50 categorias personalizadas atingido"));
    }

    @Test
    void editarMudaNomeIconeETomMasNaoOTipo() throws Exception {
        String token = cadastrarUsuario();
        Long id = criarCategoria(token, "Freela", "ENTRADA");

        mockMvc.perform(comToken(put("/api/categorias/" + id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Freelance\", \"icone\": \"laptop\", \"tom\": \"ai\", \"tipo\": \"SAIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Freelance"))
                .andExpect(jsonPath("$.icone").value("laptop"))
                .andExpect(jsonPath("$.tom").value("ai"))
                .andExpect(jsonPath("$.tipo").value("ENTRADA"));

        // Salvar com o próprio nome não é "repetido"
        mockMvc.perform(comToken(put("/api/categorias/" + id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"FREELANCE\", \"icone\": \"laptop\", \"tom\": \"ai\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void fixaECategoriaDeOutroUsuarioNaoEditamNemExcluem() throws Exception {
        String tokenDona = cadastrarUsuario();
        Long idDaDona = criarCategoria(tokenDona, "Pet", "SAIDA");
        String token = cadastrarUsuario();

        for (Long id : List.of(CATEGORIA_MORADIA, idDaDona)) {
            mockMvc.perform(comToken(put("/api/categorias/" + id), token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\": \"X\", \"icone\": \"car\", \"tom\": \"brand\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));

            mockMvc.perform(comToken(delete("/api/categorias/" + id), token))
                    .andExpect(status().isNotFound());
        }

        // E também não pode usar a categoria de outro usuário num lançamento
        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "X", "valor": 10, "tipo": "SAIDA", "contaId": %d, "categoriaId": %d}
                                """.formatted(idContaPadrao(token), idDaDona)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
    }

    @Test
    void categoriaDeTipoDiferenteDaMovimentacaoDevolve400() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Salário", "valor": 3000, "tipo": "ENTRADA", "contaId": %d, "categoriaId": %d}
                                """.formatted(contaId, CATEGORIA_MORADIA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A categoria não é do mesmo tipo da movimentação"));

        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100, "tipo": "SAIDA", "categoriaId": %d,
                                 "contaId": %d, "totalParcelas": 2}
                                """.formatted(CATEGORIA_RENDA, contaId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transacaoComCategoriaPersonalizadaDevolveIdENome() throws Exception {
        String token = cadastrarUsuario();
        Long categoriaId = criarCategoria(token, "Salário", "ENTRADA");

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Salário março", "valor": 3000, "tipo": "ENTRADA", "contaId": %d, "categoriaId": %d}
                                """.formatted(idContaPadrao(token), categoriaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaId").value(categoriaId))
                .andExpect(jsonPath("$.nomeCategoria").value("Salário"));
    }

    @Test
    void excluirMoveMovimentacoesERecorrenciasParaAFixaDoMesmoTipo() throws Exception {
        String token = cadastrarUsuario();
        Long contaId = idContaPadrao(token);
        Long pet = criarCategoria(token, "Pet", "SAIDA");
        Long salario = criarCategoria(token, "Salário", "ENTRADA");

        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Ração", "valor": 150, "tipo": "SAIDA", "contaId": %d, "categoriaId": %d,
                                 "dataTransacao": "2026-03-01"}
                                """.formatted(contaId, pet)))
                .andExpect(status().isCreated());
        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Plano pet", "valor": 50, "tipo": "SAIDA", "categoriaId": %d,
                                 "contaId": %d, "totalParcelas": 2, "dataInicio": "2026-03-10"}
                                """.formatted(pet, contaId)))
                .andExpect(status().isCreated());
        mockMvc.perform(comToken(post("/api/transacoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Salário", "valor": 3000, "tipo": "ENTRADA", "contaId": %d, "categoriaId": %d,
                                 "dataTransacao": "2026-03-05"}
                                """.formatted(contaId, salario)))
                .andExpect(status().isCreated());

        String resumoAntes = mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andReturn().getResponse().getContentAsString();

        // 1 transação avulsa + 2 parcelas + 1 recorrência
        mockMvc.perform(comToken(delete("/api/categorias/" + pet), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movidas").value(4));
        mockMvc.perform(comToken(delete("/api/categorias/" + salario), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movidas").value(1));

        String lista = mockMvc.perform(comToken(get("/api/transacoes"), token))
                .andReturn().getResponse().getContentAsString();
        List<Integer> saidas = JsonPath.read(lista, "$[?(@.tipo == 'SAIDA')].categoriaId");
        List<Integer> entradas = JsonPath.read(lista, "$[?(@.tipo == 'ENTRADA')].categoriaId");

        assertThat(saidas).hasSize(3).allMatch(id -> id == CATEGORIA_OUTRO);
        assertThat(entradas).containsExactly((int) CATEGORIA_RENDA);

        mockMvc.perform(comToken(get("/api/recorrencias"), token))
                .andExpect(jsonPath("$[0].categoriaId").value(CATEGORIA_OUTRO))
                .andExpect(jsonPath("$[0].nomeCategoria").value("Outro"));

        String resumoDepois = mockMvc.perform(comToken(get("/api/transacoes/resumo"), token))
                .andReturn().getResponse().getContentAsString();
        assertThat(resumoDepois).isEqualTo(resumoAntes);

        mockMvc.perform(comToken(get("/api/categorias"), token))
                .andExpect(jsonPath("$.length()").value(5));
    }
}
