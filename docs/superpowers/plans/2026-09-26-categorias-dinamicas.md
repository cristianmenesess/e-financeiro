# Categorias Dinâmicas — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o `enum Categoria` por uma tabela `categorias` com 5 categorias fixas do sistema + categorias personalizadas por usuário (entrada e saída), com CRUD, seção própria no front e chips dinâmicos.

**Architecture:** Migration V8 cria `categorias`, converte o texto de `transacoes.categoria`/`recorrencias.categoria` em FK `categoria_id` e remove o texto. A entidade `Categoria` (mesmo nome do enum antigo) é resolvida por `BuscadorRecursosDoUsuario.buscarCategoria` (fixas + do usuário). `ServicoCategoria`/`ControladorCategoria` expõem `/api/categorias`. O contrato de transações/recorrências passa de `categoria` (texto) pra `categoriaId` (+ `nomeCategoria` na resposta). O front carrega a lista de categorias e resolve ícone/tom por id em todo lugar.

**Tech Stack:** Java 17, Spring Boot 4.1, Spring Data JPA, Flyway, JUnit 5 + MockMvc + Testcontainers; front HTML + jQuery sem build (`npm run check`).

**Spec:** `docs/superpowers/specs/2026-09-26-categorias-dinamicas-design.md`

**Regras do projeto que valem pra todas as tasks:**
- Nunca criar commit nem mexer no estado do git — "Checkpoint" = só rodar a verificação indicada; o commit é do Cristian.
- Português em nomes/comentários/mensagens; JavaDoc em todo método público; DTOs `record`; controllers com `Authentication autenticacao` + `autenticacao.getName()`; exceções só com mensagem.
- Testes do backend exigem o Docker rodando (Testcontainers). Suíte antes deste plano: 59 testes.
- Front: `var`, padrão "Tela" (`self.x = function`), ids em inglês (`modal<Substantivo>`, `btnConfirm<Substantivo>`), nada de cor hex nem `NNpx` em string de JS, CSS com tokens (`var(--...)`) nas propriedades que o Stylelint exige.
- Não editar `application-prod.properties`, `design-system/` nem `DESIGN.md`.

**IDs das categorias fixas:** a V8 insere as 5 fixas numa ordem fixa, então num banco novo (testes) elas têm sempre `RENDA=1, DESPESA=2, ALIMENTACAO=3, MORADIA=4, OUTRO=5`. Os testes usam essas constantes; um teste de guarda (Task 2) quebra se isso mudar.

---

## Mapa de arquivos

**Backend (`D:\PROJETOS\e-financeiro`)**

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `src/main/resources/db/migration/V8__categorias_dinamicas.sql` | Criar | Tabela, fixas, conversão, remoção do texto |
| `src/main/java/com/efinanceiro/dominio/Categoria.java` | Substituir (enum → entidade) | Entidade `categorias` |
| `src/main/java/com/efinanceiro/dominio/OpcoesCategoria.java` | Criar | Ícones e tons permitidos |
| `src/main/java/com/efinanceiro/dominio/Transacao.java`, `Recorrencia.java` | Modificar | `@ManyToOne Categoria categoria` |
| `src/main/java/com/efinanceiro/repositorio/RepositorioCategoria.java` | Criar | Consultas de categoria |
| `src/main/java/com/efinanceiro/repositorio/RepositorioTransacao.java`, `RepositorioRecorrencia.java` | Modificar | `moverCategoria`, `@EntityGraph` com categoria |
| `src/main/java/com/efinanceiro/servico/BuscadorRecursosDoUsuario.java` | Modificar | `buscarCategoria` |
| `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoTransacao.java`, `RequisicaoRecorrencia.java` | Modificar | `Long categoriaId` |
| `src/main/java/com/efinanceiro/dto/resposta/RespostaTransacao.java`, `RespostaRecorrencia.java` | Modificar | `categoriaId`, `nomeCategoria` |
| `src/main/java/com/efinanceiro/servico/ServicoTransacao.java`, `ServicoRecorrencia.java` | Modificar | Resolver categoria + checar tipo |
| `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoCategoria.java`, `RequisicaoAtualizacaoCategoria.java` | Criar | Entrada do CRUD |
| `src/main/java/com/efinanceiro/dto/resposta/RespostaCategoria.java`, `RespostaExclusaoCategoria.java`, `RespostaOpcoesCategoria.java` | Criar | Saída do CRUD |
| `src/main/java/com/efinanceiro/servico/ServicoCategoria.java` | Criar | Regras do CRUD |
| `src/main/java/com/efinanceiro/controlador/ControladorCategoria.java` | Criar | `/api/categorias` |
| `src/main/java/com/efinanceiro/servico/ServicoPerfil.java` | Modificar | Exclusão do cadastro apaga as categorias |
| `src/test/java/com/efinanceiro/suporte/TesteIntegracao.java` | Modificar | Constantes de categoria, `JdbcTemplate`, helper |
| `src/test/java/...` (testes existentes com `"categoria"`) | Modificar | `categoriaId` |
| `src/test/java/com/efinanceiro/servico/MigracaoCategoriasTeste.java` | Criar | Conversão da V8 |
| `src/test/java/com/efinanceiro/controlador/CategoriaTeste.java` | Criar | CRUD e regras |
| `README.md` | Modificar | Tabela "Categorias" e contrato novo |

**Front (`D:\PROJETOS\e-financeiro-front`)**

| Arquivo | Ação |
|---|---|
| `index.html` | Nav "Categorias" (sidebar + tabbar), `#pageCategorias`, `#modalCategoria`, chips estáticos removidos |
| `assets/css/style.css` | Grupos da página, seletor de ícones, prévia |
| `assets/js/script.js` | Estado, carregamento, resolução por id, chips, filtros, gráfico, CRUD |
| `sw.js` | Cache `v6` |

---

### Task 1: Backend — modelo, migration V8 e contrato de transações/recorrências

Mudança atômica: a V8 remove a coluna texto, então entidade, DTOs, serviços e testes existentes precisam mudar juntos pro contexto subir (`ddl-auto=validate`).

**Files:** todos os marcados "Modificar/Substituir" do backend no mapa acima, exceto os do CRUD (Task 2) e `ServicoPerfil` (Task 3); mais `MigracaoCategoriasTeste.java`.

- [ ] **Step 1: Teste da migration (falha primeiro)** — criar `src/test/java/com/efinanceiro/servico/MigracaoCategoriasTeste.java`:

```java
package com.efinanceiro.servico;

import com.efinanceiro.suporte.TesteIntegracao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V8 mexe em dado de produção (converte o texto da categoria em FK e apaga a coluna texto).
 * Aqui o Flyway roda num schema separado até a V7, recebe dados no formato antigo e só então
 * aplica a V8 — igual vai acontecer no banco de produção.
 */
class MigracaoCategoriasTeste extends TesteIntegracao {

    private static final String SCHEMA = "teste_migracao";

    @Autowired
    private DataSource dataSource;

    private Flyway flywayAte(String versao) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(versao)
                .load();
    }

    @Test
    void v8ConverteOTextoAntigoParaACategoriaFixaCerta() {
        try {
            flywayAte("7").migrate();

            jdbcTemplate.update("insert into " + SCHEMA + ".usuarios (nome, email, senha_hash) values ('Antigo', 'antigo@teste.com', 'x')");
            Long usuarioId = jdbcTemplate.queryForObject("select id from " + SCHEMA + ".usuarios", Long.class);
            jdbcTemplate.update("insert into " + SCHEMA + ".contas (usuario_id, nome, cor_fundo, cor_texto) values (?, 'Pessoal', '#FFFFFF', '#000000')", usuarioId);
            Long contaId = jdbcTemplate.queryForObject("select id from " + SCHEMA + ".contas", Long.class);

            for (String codigo : List.of("RENDA", "DESPESA", "ALIMENTACAO", "MORADIA", "OUTRO")) {
                jdbcTemplate.update("insert into " + SCHEMA + ".transacoes (usuario_id, descricao, valor, tipo, categoria, conta_id, data_transacao) "
                        + "values (?, ?, 10, 'SAIDA', ?, ?, current_date)", usuarioId, codigo, codigo, contaId);
            }

            jdbcTemplate.update("insert into " + SCHEMA + ".recorrencias (usuario_id, conta_id, descricao, valor, tipo, categoria, total_parcelas, data_inicio) "
                    + "values (?, ?, 'Aluguel', 100, 'SAIDA', 'MORADIA', 3, current_date)", usuarioId, contaId);

            flywayAte("8").migrate();

            List<Map<String, Object>> transacoes = jdbcTemplate.queryForList(
                    "select t.descricao, c.codigo from " + SCHEMA + ".transacoes t join " + SCHEMA + ".categorias c on c.id = t.categoria_id");
            assertThat(transacoes).hasSize(5);
            transacoes.forEach(linha -> assertThat(linha.get("codigo")).isEqualTo(linha.get("descricao")));

            assertThat(jdbcTemplate.queryForObject("select c.codigo from " + SCHEMA + ".recorrencias r join " + SCHEMA
                    + ".categorias c on c.id = r.categoria_id", String.class)).isEqualTo("MORADIA");

            assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema = ? "
                    + "and table_name in ('transacoes', 'recorrencias') and column_name = 'categoria'", Integer.class, SCHEMA)).isZero();

            assertThat(jdbcTemplate.queryForList("select codigo from " + SCHEMA + ".categorias where usuario_id is null order by id", String.class))
                    .containsExactly("RENDA", "DESPESA", "ALIMENTACAO", "MORADIA", "OUTRO");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }
}
```

- [ ] **Step 2: `JdbcTemplate` e constantes na base de testes** — em `TesteIntegracao.java`: adicionar `import org.springframework.jdbc.core.JdbcTemplate;`, o campo

```java
    @Autowired
    protected JdbcTemplate jdbcTemplate;
```

e, logo depois de `SENHA_PADRAO`, as constantes:

```java
    /** Ids das categorias fixas: a V8 sempre as insere nessa ordem num banco novo. */
    public static final long CATEGORIA_RENDA = 1L;
    public static final long CATEGORIA_DESPESA = 2L;
    public static final long CATEGORIA_ALIMENTACAO = 3L;
    public static final long CATEGORIA_MORADIA = 4L;
    public static final long CATEGORIA_OUTRO = 5L;
```

Em `PerfilTeste.java`, apagar o campo privado `jdbcTemplate` e o `@Autowired` dele e o import `org.springframework.jdbc.core.JdbcTemplate` (passa a usar o da base).

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests "com.efinanceiro.servico.MigracaoCategoriasTeste" --no-daemon`
Expected: FAIL — `flywayAte("8")` aplica só até a V7 (não existe V8) e a consulta em `teste_migracao.categorias` falha com `relation "teste_migracao.categorias" does not exist`.

- [ ] **Step 4: Criar a migration** `src/main/resources/db/migration/V8__categorias_dinamicas.sql`:

```sql
-- Categorias deixam de ser um enum fixo: fixas do sistema (usuario_id nulo) + personalizadas por usuário
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios (id),
    codigo VARCHAR(20) UNIQUE,
    nome VARCHAR(40) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    icone VARCHAR(40) NOT NULL,
    tom VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_categorias_usuario_id ON categorias (usuario_id);

-- Ordem importa: os testes contam com RENDA=1 ... OUTRO=5 num banco novo
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('RENDA', 'Renda', 'ENTRADA', 'banknote', 'positive');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('DESPESA', 'Despesa', 'SAIDA', 'receipt', 'negative');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('ALIMENTACAO', 'Alimentação', 'SAIDA', 'shopping-bag', 'warning');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('MORADIA', 'Moradia', 'SAIDA', 'house', 'brand');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('OUTRO', 'Outro', 'SAIDA', 'ellipsis', 'neutral');

-- Transações: FK preenchida a partir do texto antigo; qualquer valor desconhecido vira OUTRO
ALTER TABLE transacoes ADD COLUMN categoria_id BIGINT REFERENCES categorias (id);
UPDATE transacoes t SET categoria_id = c.id FROM categorias c WHERE c.codigo = t.categoria;
UPDATE transacoes SET categoria_id = (SELECT id FROM categorias WHERE codigo = 'OUTRO') WHERE categoria_id IS NULL;
ALTER TABLE transacoes ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE transacoes DROP COLUMN categoria;
CREATE INDEX idx_transacoes_categoria_id ON transacoes (categoria_id);

-- Recorrências: mesma conversão
ALTER TABLE recorrencias ADD COLUMN categoria_id BIGINT REFERENCES categorias (id);
UPDATE recorrencias r SET categoria_id = c.id FROM categorias c WHERE c.codigo = r.categoria;
UPDATE recorrencias SET categoria_id = (SELECT id FROM categorias WHERE codigo = 'OUTRO') WHERE categoria_id IS NULL;
ALTER TABLE recorrencias ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE recorrencias DROP COLUMN categoria;
CREATE INDEX idx_recorrencias_categoria_id ON recorrencias (categoria_id);
```

- [ ] **Step 5: Entidade `Categoria`** — substituir TODO o conteúdo de `src/main/java/com/efinanceiro/dominio/Categoria.java` (hoje é o enum):

```java
package com.efinanceiro.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nulo nas categorias fixas do sistema, que valem pra todos os usuários
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Só nas fixas (RENDA, DESPESA, ALIMENTACAO, MORADIA, OUTRO): o código acha "Renda"/"Outro" sem depender de id
    @Column(length = 20, unique = true)
    private String codigo;

    @Column(nullable = false, length = 40)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacao tipo;

    @Column(nullable = false, length = 40)
    private String icone;

    @Column(nullable = false, length = 20)
    private String tom;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    /**
     * Indica se é uma categoria fixa do sistema (sem dono), que não pode ser editada nem excluída.
     *
     * @return true se for fixa
     */
    public boolean isFixa() {
        return usuario == null;
    }

    @PrePersist
    private void aoPersistir() {
        this.criadoEm = Instant.now();
    }
}
```

- [ ] **Step 6: Criar `OpcoesCategoria.java`** em `dominio/`:

```java
package com.efinanceiro.dominio;

import java.util.List;

/**
 * Ícones (nomes Lucide) e tons (do design system do front) que uma categoria pode usar. Fica só
 * aqui: o front busca a lista pela API pra montar os seletores, então os dois nunca divergem.
 */
public final class OpcoesCategoria {

    // Só os tons que têm classe ef-icon-tile--<tom> no design system (neutral = tile padrão)
    public static final List<String> TONS = List.of("brand", "positive", "negative", "warning", "ai", "neutral");

    public static final List<String> ICONES = List.of(
            "banknote", "receipt", "shopping-bag", "house", "ellipsis",
            "car", "fuel", "bus", "plane", "heart-pulse", "pill", "graduation-cap", "book-open",
            "gamepad-2", "film", "music", "shirt", "dog", "baby", "gift", "dumbbell", "utensils",
            "coffee", "smartphone", "wifi", "zap", "wrench", "briefcase", "laptop", "piggy-bank",
            "trending-up", "landmark", "hand-coins"
    );

    private OpcoesCategoria() {
    }
}
```

- [ ] **Step 7: `Transacao` e `Recorrencia`** — nos dois arquivos, trocar o bloco

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;
```

por

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;
```

(os imports de `EnumType`/`Enumerated` continuam sendo usados pelo campo `tipo`).

- [ ] **Step 8: Criar `RepositorioCategoria.java`**:

```java
package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepositorioCategoria extends JpaRepository<Categoria, Long> {

    /**
     * Lista as categorias que o usuário enxerga: as fixas do sistema e as dele. Left join porque
     * as fixas não têm usuário (um join comum as deixaria de fora).
     *
     * @param usuarioId Id do usuário
     * @return Fixas + personalizadas do usuário, sem ordem definida
     */
    @Query("select c from Categoria c left join c.usuario u where u is null or u.id = :usuarioId")
    List<Categoria> listarVisiveisAoUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Busca uma categoria que o usuário pode usar num lançamento (fixa ou dele).
     *
     * @param id Id da categoria
     * @param usuarioId Id do usuário
     * @return Categoria, se existir e for fixa ou do usuário
     */
    @Query("select c from Categoria c left join c.usuario u where c.id = :id and (u is null or u.id = :usuarioId)")
    Optional<Categoria> buscarVisivelAoUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    /**
     * Busca uma categoria personalizada do usuário (fixas nunca voltam aqui) — usado pra editar e excluir.
     *
     * @param id Id da categoria
     * @param usuarioId Id do usuário dono
     * @return Categoria personalizada do usuário, se existir
     */
    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Busca uma categoria fixa pelo código (ex: OUTRO, RENDA).
     *
     * @param codigo Código da categoria fixa
     * @return Categoria fixa, se existir
     */
    Optional<Categoria> findByCodigo(String codigo);

    /**
     * Conta as categorias personalizadas do usuário — usado no limite por usuário.
     *
     * @param usuarioId Id do usuário
     * @return Quantidade de categorias personalizadas
     */
    long countByUsuarioId(Long usuarioId);

    /**
     * Verifica se já existe, entre as fixas e as do usuário, outra categoria com o mesmo nome
     * (ignorando maiúsculas/minúsculas).
     *
     * @param nome Nome a verificar (já sem espaços nas pontas)
     * @param usuarioId Id do usuário
     * @param idIgnorado Id da própria categoria numa edição (0 na criação)
     * @return true se o nome já estiver em uso
     */
    @Query("""
            select count(c) > 0 from Categoria c left join c.usuario u
            where lower(c.nome) = lower(:nome) and (u is null or u.id = :usuarioId) and c.id <> :idIgnorado
            """)
    boolean existeNomeVisivel(@Param("nome") String nome, @Param("usuarioId") Long usuarioId, @Param("idIgnorado") Long idIgnorado);

    /**
     * Apaga todas as categorias personalizadas de um usuário, num único DELETE — usado na exclusão
     * do cadastro (as fixas não têm usuário e nunca são apagadas).
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Categoria c where c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
```

- [ ] **Step 9: `RepositorioTransacao`** — nas duas listagens (`findByUsuarioId(Long, Pageable)` e `findByUsuarioIdAndContaId`), trocar `@EntityGraph(attributePaths = {"conta", "cartao", "recorrencia"})` por `@EntityGraph(attributePaths = {"conta", "cartao", "recorrencia", "categoria"})`; adicionar o import `com.efinanceiro.dominio.Categoria` e, antes do último `}`:

```java
    /**
     * Move todas as transações de uma categoria pra outra, num único UPDATE — usado ao excluir uma
     * categoria personalizada.
     *
     * @param origem Categoria que vai ser excluída
     * @param destino Categoria fixa que recebe as transações
     * @return Quantidade de transações movidas
     */
    @Modifying(flushAutomatically = true)
    @Query("update Transacao t set t.categoria = :destino where t.categoria = :origem")
    int moverCategoria(@Param("origem") Categoria origem, @Param("destino") Categoria destino);
```

- [ ] **Step 10: `RepositorioRecorrencia`** — nas duas consultas com `@EntityGraph(attributePaths = {"conta", "cartao"})`, trocar por `{"conta", "cartao", "categoria"}`; adicionar o import `com.efinanceiro.dominio.Categoria` e, antes do último `}`:

```java
    /**
     * Move todas as recorrências de uma categoria pra outra, num único UPDATE — usado ao excluir uma
     * categoria personalizada.
     *
     * @param origem Categoria que vai ser excluída
     * @param destino Categoria fixa que recebe as recorrências
     * @return Quantidade de recorrências movidas
     */
    @Modifying(flushAutomatically = true)
    @Query("update Recorrencia r set r.categoria = :destino where r.categoria = :origem")
    int moverCategoria(@Param("origem") Categoria origem, @Param("destino") Categoria destino);
```

- [ ] **Step 11: `BuscadorRecursosDoUsuario`** — adicionar o campo/parâmetro de construtor `RepositorioCategoria repositorioCategoria` (import `com.efinanceiro.repositorio.RepositorioCategoria` e `com.efinanceiro.dominio.Categoria`) e o método, antes do último `}`:

```java
    /**
     * Busca uma categoria que o usuário pode usar: uma fixa do sistema ou uma personalizada dele.
     *
     * @param categoriaId Id da categoria
     * @param usuarioId Id do usuário
     * @return Categoria encontrada
     */
    public Categoria buscarCategoria(Long categoriaId, Long usuarioId) {
        return repositorioCategoria.buscarVisivelAoUsuario(categoriaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }
```

- [ ] **Step 12: DTOs de requisição** — em `RequisicaoTransacao.java` e `RequisicaoRecorrencia.java`: apagar `import com.efinanceiro.dominio.Categoria;` e trocar

```java
        @NotNull(message = "A categoria é obrigatória")
        Categoria categoria,
```

por

```java
        @NotNull(message = "A categoria é obrigatória")
        Long categoriaId,
```

- [ ] **Step 13: DTOs de resposta** — em `RespostaTransacao.java` e `RespostaRecorrencia.java`: apagar `import com.efinanceiro.dominio.Categoria;` e trocar a linha `Categoria categoria,` por:

```java
        Long categoriaId,
        String nomeCategoria,
```

- [ ] **Step 14: `ServicoTransacao`** — adicionar os imports `com.efinanceiro.dominio.Categoria` e `com.efinanceiro.excecao.DadosInvalidosException`; em `preencherTransacao`, trocar `transacao.setCategoria(requisicao.categoria());` por:

```java
        transacao.setCategoria(resolverCategoria(requisicao.categoriaId(), requisicao.tipo(), usuario));
```

adicionar o método privado logo depois de `preencherTransacao`:

```java
    private Categoria resolverCategoria(Long categoriaId, TipoTransacao tipo, Usuario usuario) {
        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(categoriaId, usuario.getId());

        if (categoria.getTipo() != tipo) {
            throw new DadosInvalidosException("A categoria não é do mesmo tipo da movimentação");
        }

        return categoria;
    }
```

e em `paraResposta`, trocar a linha `transacao.getCategoria(),` por:

```java
                transacao.getCategoria().getId(),
                transacao.getCategoria().getNome(),
```

- [ ] **Step 15: `ServicoRecorrencia`** — adicionar os imports `com.efinanceiro.dominio.Categoria` e `com.efinanceiro.excecao.DadosInvalidosException`; em `criarRecorrencia`, logo depois da linha que resolve o `cartao`, adicionar:

```java
        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(requisicao.categoriaId(), usuario.getId());

        if (categoria.getTipo() != requisicao.tipo()) {
            throw new DadosInvalidosException("A categoria não é do mesmo tipo da movimentação");
        }
```

trocar `recorrencia.setCategoria(requisicao.categoria());` por `recorrencia.setCategoria(categoria);` e `transacao.setCategoria(requisicao.categoria());` por `transacao.setCategoria(categoria);`; em `paraResposta`, trocar `recorrencia.getCategoria(),` por:

```java
                recorrencia.getCategoria().getId(),
                recorrencia.getCategoria().getNome(),
```

- [ ] **Step 16: Testes existentes pro contrato novo** —
  a) Em `TesteIntegracao.criarTransacao`, trocar o corpo do `String corpo = ...` por:

```java
        long categoriaId = "ENTRADA".equals(tipo) ? CATEGORIA_RENDA : CATEGORIA_OUTRO;
        String corpo = """
                {"descricao": "Lançamento", "valor": %s, "tipo": "%s", "contaId": %d, "categoriaId": %d,
                 "cartaoId": %s, "dataTransacao": %s}
                """.formatted(valor, tipo, contaId, categoriaId, cartaoId, data == null ? "null" : "\"" + data + "\"");
```

  b) Nos demais testes, substituir literalmente dentro dos JSON (Git Bash, na pasta do backend):

```bash
cd /d/PROJETOS/e-financeiro && grep -rl '"categoria": "' src/test/java | xargs sed -i \
  -e 's/"categoria": "RENDA"/"categoriaId": 1/g' \
  -e 's/"categoria": "DESPESA"/"categoriaId": 2/g' \
  -e 's/"categoria": "ALIMENTACAO"/"categoriaId": 3/g' \
  -e 's/"categoria": "MORADIA"/"categoriaId": 4/g' \
  -e 's/"categoria": "OUTRO"/"categoriaId": 5/g'
grep -rn '"categoria"' src/test/java
```

  Expected: o último `grep` não encontra nada.

  c) Em `TransacaoTeste.listaSemPaginacaoContinuaDevolvendoTudoNoMesmoFormato`, trocar `.andExpect(jsonPath("$[0].categoria").value("OUTRO"))` por:

```java
                .andExpect(jsonPath("$[0].categoriaId").value(5))
                .andExpect(jsonPath("$[0].nomeCategoria").value("Outro"))
```

- [ ] **Step 17: Rodar a suíte inteira**

Run: `./gradlew clean build --no-daemon`
Expected: BUILD SUCCESSFUL, 60 testes (59 + `MigracaoCategoriasTeste`), sem avisos de lint. Se algum teste existente falhar com 400 "A categoria não é do mesmo tipo da movimentação", o JSON dele manda um tipo incompatível com a categoria — corrigir o **teste** trocando pela categoria fixa do tipo certo (RENDA=1 pra ENTRADA, OUTRO=5 pra SAIDA) e reportar qual foi.

- [ ] **Step 18: Checkpoint** — commit fica com o Cristian.

---

### Task 2: Backend — CRUD de categorias (TDD)

**Files:**
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoCategoria.java`, `RequisicaoAtualizacaoCategoria.java`
- Create: `src/main/java/com/efinanceiro/dto/resposta/RespostaCategoria.java`, `RespostaExclusaoCategoria.java`, `RespostaOpcoesCategoria.java`
- Create: `src/main/java/com/efinanceiro/servico/ServicoCategoria.java`
- Create: `src/main/java/com/efinanceiro/controlador/ControladorCategoria.java`
- Test: `src/test/java/com/efinanceiro/controlador/CategoriaTeste.java`

- [ ] **Step 1: Testes (falham primeiro)** — criar `CategoriaTeste.java`:

```java
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
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "com.efinanceiro.controlador.CategoriaTeste" --no-daemon`
Expected: FAIL — todos com 404 "Rota não encontrada" (`/api/categorias` não existe).

- [ ] **Step 3: DTOs** — criar os cinco:

```java
package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.TipoTransacao;

public record RespostaCategoria(
        Long id,
        String nome,
        TipoTransacao tipo,
        String icone,
        String tom,
        boolean fixa
) {
}
```

```java
package com.efinanceiro.dto.resposta;

/**
 * Resultado da exclusão de uma categoria: quantas transações + recorrências foram movidas pra
 * categoria fixa genérica do mesmo tipo.
 */
public record RespostaExclusaoCategoria(long movidas) {
}
```

```java
package com.efinanceiro.dto.resposta;

import java.util.List;

public record RespostaOpcoesCategoria(
        List<String> icones,
        List<String> tons
) {
}
```

```java
package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequisicaoCategoria(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 40, message = "O nome da categoria deve ter no máximo 40 caracteres")
        String nome,

        @NotNull(message = "O tipo (entrada ou saída) é obrigatório")
        TipoTransacao tipo,

        @NotBlank(message = "O ícone é obrigatório")
        String icone,

        @NotBlank(message = "A cor é obrigatória")
        String tom
)
{}
```

```java
package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sem o tipo: trocar entrada <-> saída deixaria as movimentações da categoria com o tipo errado
public record RequisicaoAtualizacaoCategoria(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(max = 40, message = "O nome da categoria deve ter no máximo 40 caracteres")
        String nome,

        @NotBlank(message = "O ícone é obrigatório")
        String icone,

        @NotBlank(message = "A cor é obrigatória")
        String tom
)
{}
```

- [ ] **Step 4: `ServicoCategoria.java`**:

```java
package com.efinanceiro.servico;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.OpcoesCategoria;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoCategoria;
import com.efinanceiro.dto.requisicao.RequisicaoCategoria;
import com.efinanceiro.dto.resposta.RespostaCategoria;
import com.efinanceiro.dto.resposta.RespostaExclusaoCategoria;
import com.efinanceiro.dto.resposta.RespostaOpcoesCategoria;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.RecursoDuplicadoException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.excecao.RegraDeNegocioException;
import com.efinanceiro.repositorio.RepositorioCategoria;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ServicoCategoria {

    private static final int LIMITE_PERSONALIZADAS = 50;

    // Fixas primeiro (na ordem de criação), depois as personalizadas por nome
    private static final Comparator<Categoria> ORDEM = Comparator
            .comparing((Categoria categoria) -> categoria.isFixa() ? 0 : 1)
            .thenComparing(categoria -> categoria.isFixa() ? "" : categoria.getNome().toLowerCase(Locale.ROOT))
            .thenComparing(Categoria::getId);

    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;

    public ServicoCategoria(RepositorioCategoria repositorioCategoria,
                             RepositorioTransacao repositorioTransacao,
                             RepositorioRecorrencia repositorioRecorrencia,
                             BuscadorRecursosDoUsuario buscadorRecursosDoUsuario) {
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
    }

    /**
     * Lista as categorias que o usuário pode usar: as fixas do sistema e as personalizadas dele.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Fixas primeiro, depois as personalizadas em ordem alfabética
     */
    @Transactional(readOnly = true)
    public List<RespostaCategoria> listarCategorias(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        return repositorioCategoria.listarVisiveisAoUsuario(usuario.getId()).stream()
                .sorted(ORDEM)
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Ícones e tons que uma categoria pode usar.
     *
     * @return Listas de ícones (nomes Lucide) e tons do design system
     */
    @Transactional(readOnly = true)
    public RespostaOpcoesCategoria listarOpcoes() {
        return new RespostaOpcoesCategoria(OpcoesCategoria.ICONES, OpcoesCategoria.TONS);
    }

    /**
     * Cria uma categoria personalizada pro usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Nome, tipo, ícone e tom
     * @return Categoria criada
     */
    public RespostaCategoria criarCategoria(String emailUsuario, RequisicaoCategoria requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        if (repositorioCategoria.countByUsuarioId(usuario.getId()) >= LIMITE_PERSONALIZADAS) {
            throw new RegraDeNegocioException("Limite de " + LIMITE_PERSONALIZADAS + " categorias personalizadas atingido");
        }

        String nome = requisicao.nome().trim();
        validarIconeETom(requisicao.icone(), requisicao.tom());
        validarNomeLivre(nome, usuario.getId(), 0L);

        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(nome);
        categoria.setTipo(requisicao.tipo());
        categoria.setIcone(requisicao.icone());
        categoria.setTom(requisicao.tom());

        repositorioCategoria.save(categoria);
        return paraResposta(categoria);
    }

    /**
     * Atualiza nome, ícone e tom de uma categoria personalizada do usuário (o tipo não muda).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da categoria
     * @param requisicao Novo nome, ícone e tom
     * @return Categoria atualizada
     */
    public RespostaCategoria atualizarCategoria(String emailUsuario, Long id, RequisicaoAtualizacaoCategoria requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Categoria categoria = buscarPersonalizadaDoUsuario(id, usuario.getId());

        String nome = requisicao.nome().trim();
        validarIconeETom(requisicao.icone(), requisicao.tom());
        validarNomeLivre(nome, usuario.getId(), categoria.getId());

        categoria.setNome(nome);
        categoria.setIcone(requisicao.icone());
        categoria.setTom(requisicao.tom());

        repositorioCategoria.save(categoria);
        return paraResposta(categoria);
    }

    /**
     * Exclui uma categoria personalizada do usuário. As transações e recorrências dela passam pra
     * categoria fixa genérica do mesmo tipo (Outro pra saída, Renda pra entrada) — nada se perde.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da categoria
     * @return Quantidade de transações + recorrências movidas
     */
    public RespostaExclusaoCategoria excluirCategoria(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Categoria categoria = buscarPersonalizadaDoUsuario(id, usuario.getId());

        String codigoDestino = categoria.getTipo() == TipoTransacao.ENTRADA ? "RENDA" : "OUTRO";
        Categoria destino = repositorioCategoria.findByCodigo(codigoDestino)
                .orElseThrow(() -> new IllegalStateException("Categoria fixa " + codigoDestino + " não existe no banco"));

        long movidas = repositorioTransacao.moverCategoria(categoria, destino)
                + repositorioRecorrencia.moverCategoria(categoria, destino);

        repositorioCategoria.delete(categoria);
        return new RespostaExclusaoCategoria(movidas);
    }

    private Categoria buscarPersonalizadaDoUsuario(Long id, Long usuarioId) {
        return repositorioCategoria.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private void validarIconeETom(String icone, String tom) {
        if (!OpcoesCategoria.ICONES.contains(icone)) {
            throw new DadosInvalidosException("Ícone inválido");
        }

        if (!OpcoesCategoria.TONS.contains(tom)) {
            throw new DadosInvalidosException("Cor inválida");
        }
    }

    private void validarNomeLivre(String nome, Long usuarioId, Long idIgnorado) {
        if (repositorioCategoria.existeNomeVisivel(nome, usuarioId, idIgnorado)) {
            throw new RecursoDuplicadoException("Já existe uma categoria com esse nome");
        }
    }

    private RespostaCategoria paraResposta(Categoria categoria) {
        return new RespostaCategoria(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getIcone(), categoria.getTom(), categoria.isFixa());
    }
}
```

**Exceção nova (Step 4b):** o nome repetido usa `RecursoDuplicadoException` — ver o próximo passo.

- [ ] **Step 4b: `RecursoDuplicadoException`** — criar `src/main/java/com/efinanceiro/excecao/RecursoDuplicadoException.java`:

```java
package com.efinanceiro.excecao;

public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
```

e, no `TratadorGlobalDeExcecoes`, logo depois do handler de `EmailJaCadastradoException`:

```java
    /**
     * Trata tentativa de criar algo que já existe com o mesmo nome (ex: categoria repetida).
     *
     * @param excecao Exceção de recurso duplicado
     * @return Corpo de erro com status 409
     */
    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> tratarRecursoDuplicado(RecursoDuplicadoException excecao) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpoDeErro(excecao.getMessage()));
    }
```

- [ ] **Step 5: `ControladorCategoria.java`**:

```java
package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoCategoria;
import com.efinanceiro.dto.requisicao.RequisicaoCategoria;
import com.efinanceiro.dto.resposta.RespostaCategoria;
import com.efinanceiro.dto.resposta.RespostaExclusaoCategoria;
import com.efinanceiro.dto.resposta.RespostaOpcoesCategoria;
import com.efinanceiro.servico.ServicoCategoria;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class ControladorCategoria {

    private final ServicoCategoria servicoCategoria;

    public ControladorCategoria(ServicoCategoria servicoCategoria) {
        this.servicoCategoria = servicoCategoria;
    }

    /**
     * Lista as categorias fixas do sistema e as personalizadas do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Lista de categorias
     */
    @GetMapping
    public ResponseEntity<List<RespostaCategoria>> listarCategorias(Authentication autenticacao) {
        return ResponseEntity.ok(servicoCategoria.listarCategorias(autenticacao.getName()));
    }

    /**
     * Lista os ícones e tons que uma categoria pode usar.
     *
     * @return Ícones e tons permitidos
     */
    @GetMapping("/opcoes")
    public ResponseEntity<RespostaOpcoesCategoria> listarOpcoes() {
        return ResponseEntity.ok(servicoCategoria.listarOpcoes());
    }

    /**
     * Cria uma categoria personalizada.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Nome, tipo, ícone e tom
     * @return Categoria criada
     */
    @PostMapping
    public ResponseEntity<RespostaCategoria> criarCategoria(Authentication autenticacao,
                                                            @Valid @RequestBody RequisicaoCategoria requisicao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicoCategoria.criarCategoria(autenticacao.getName(), requisicao));
    }

    /**
     * Atualiza nome, ícone e tom de uma categoria personalizada.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da categoria
     * @param requisicao Novo nome, ícone e tom
     * @return Categoria atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaCategoria> atualizarCategoria(Authentication autenticacao,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody RequisicaoAtualizacaoCategoria requisicao) {
        return ResponseEntity.ok(servicoCategoria.atualizarCategoria(autenticacao.getName(), id, requisicao));
    }

    /**
     * Exclui uma categoria personalizada, movendo as movimentações dela pra categoria fixa genérica.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da categoria
     * @return Quantidade de movimentações e recorrências movidas
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<RespostaExclusaoCategoria> excluirCategoria(Authentication autenticacao, @PathVariable Long id) {
        return ResponseEntity.ok(servicoCategoria.excluirCategoria(autenticacao.getName(), id));
    }
}
```

- [ ] **Step 6: Rodar os testes de categoria** — `./gradlew test --tests "com.efinanceiro.controlador.CategoriaTeste" --no-daemon` → PASS (11 testes).

- [ ] **Step 7: Suíte inteira** — `./gradlew clean build --no-daemon` → BUILD SUCCESSFUL, 71 testes, sem avisos.

- [ ] **Step 8: Checkpoint** — commit fica com o Cristian.

---

### Task 3: Backend — exclusão do cadastro apaga as categorias + README

**Files:**
- Modify: `src/main/java/com/efinanceiro/servico/ServicoPerfil.java`
- Modify: `src/test/java/com/efinanceiro/controlador/PerfilTeste.java`
- Modify: `README.md`

- [ ] **Step 1: Teste (falha primeiro)** — em `PerfilTeste.excluirCadastroApagaTodosOsDadosDoUsuarioEPreservaOsDosOutros`, logo depois da linha `Long usuarioId = jdbcTemplate.queryForObject(...)`, criar uma categoria personalizada:

```java
        mockMvc.perform(comToken(post("/api/categorias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Pet\", \"tipo\": \"SAIDA\", \"icone\": \"dog\", \"tom\": \"warning\"}"))
                .andExpect(status().isCreated());
```

trocar o array do `for` de verificação por `new String[]{"transacoes", "recorrencias", "categorias", "cartoes", "contas"}`, e logo depois do `for` adicionar:

```java
        assertThat(jdbcTemplate.queryForObject("select count(*) from categorias where usuario_id is null", Integer.class)).isEqualTo(5);
```

- [ ] **Step 2: Rodar e ver falhar** — `./gradlew test --tests "com.efinanceiro.controlador.PerfilTeste" --no-daemon` → FAIL no teste de exclusão: `DataIntegrityViolationException`/409 (a FK `categorias.usuario_id` impede apagar o usuário).

- [ ] **Step 3: `ServicoPerfil`** — adicionar a dependência `RepositorioCategoria repositorioCategoria` (campo, parâmetro de construtor e import) e, em `excluirCadastro`, entre `repositorioRecorrencia.deleteByUsuarioId(usuarioId);` e `repositorioCartao.deleteByUsuarioId(usuarioId);`:

```java
        repositorioCategoria.deleteByUsuarioId(usuarioId);
```

e atualizar o JavaDoc do método pra listar "categorias personalizadas" entre os dados apagados.

- [ ] **Step 4: Suíte inteira** — `./gradlew clean build --no-daemon` → 71 testes verdes.

- [ ] **Step 5: README** — em `## Funcionalidades`, depois de `**Controle de Transações:**`:

```markdown
- **Categorias:** 5 categorias fixas do sistema (Renda, Despesa, Alimentação, Moradia, Outro) + categorias de entrada e saída criadas por cada usuário, com ícone e cor.
```

Na tabela de Transações, trocar a descrição de `POST /api/transacoes` por `Cria uma transação (com categoriaId de uma categoria do mesmo tipo)`. Depois da seção `### Recorrências`:

```markdown
### Categorias
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/categorias` | Categorias fixas do sistema + as personalizadas do usuário |
| GET | `/api/categorias/opcoes` | Ícones e cores permitidos para uma categoria |
| POST | `/api/categorias` | Cria uma categoria personalizada (nome, tipo, ícone e cor; até 50 por usuário) |
| PUT | `/api/categorias/{id}` | Edita nome, ícone e cor (o tipo não muda) |
| DELETE | `/api/categorias/{id}` | Exclui a categoria e move as movimentações dela para "Outro" (saída) ou "Renda" (entrada) |
```

- [ ] **Step 6: Checkpoint** — commit fica com o Cristian.

---

### Task 4: Front — categorias carregadas da API e resolvidas por id

**Files:** `D:\PROJETOS\e-financeiro-front\assets\js\script.js`

- [ ] **Step 1: Estado** — apagar o array `self.categoriasDisponiveis = [...]` inteiro. Em `self.state`, depois de `recorrencias: []`, adicionar (lembrar da vírgula):

```javascript
        categorias: [],
        opcoesCategoria: { icones: [], tons: [] },
        editingCategoriaId: null,
        categoriaIconeSelecionado: null,
        categoriaTomSelecionado: null
```

- [ ] **Step 2: Resolução por id** — trocar o método `self.resolveIconeCategoria` inteiro (com o JSDoc) por:

```javascript
    self.getCategoriaById = function (id) {
        return self.state.categorias.find(function (categoria) { return categoria.id === id; }) || null;
    };

    /**
     * Tom do IconTile, cor de série (donut) e ícone de uma categoria, a partir da lista carregada da
     * API. Categoria desconhecida (ex.: excluída em outro aparelho) usa o visual de "Outro".
     *
     * @param {number} categoriaId id da categoria
     * @returns {object} { classe, cor, icone }
     */
    self.resolveIconeCategoria = function (categoriaId) {
        var categoria = self.getCategoriaById(categoriaId);

        if (!categoria) {
            return { classe: '', cor: 'var(--tone-neutral-accent)', icone: 'ellipsis' };
        }

        return {
            classe: categoria.tom === 'neutral' ? '' : 'ef-icon-tile--' + categoria.tom,
            cor: 'var(--tone-' + categoria.tom + '-accent)',
            icone: categoria.icone
        };
    };
```

No JSDoc de `self.criarTileCategoria`, trocar `@param {string} categoria categoria da API` por `@param {number} categoriaId id da categoria` e o parâmetro/uso `categoria` por `categoriaId`.

- [ ] **Step 3: Usos pelo id** —
  - `buildTransactionItem`: `self.criarTileCategoria(tx.categoria)` → `self.criarTileCategoria(tx.categoriaId)`.
  - `buildRecorrenciaItem`: `self.criarTileCategoria(recorrencia.categoria)` → `self.criarTileCategoria(recorrencia.categoriaId)`.
  - `aplicarFiltros`: `self.state.categoriasFiltradas.indexOf(tx.categoria)` → `self.state.categoriasFiltradas.indexOf(tx.categoriaId)`.

- [ ] **Step 4: Gráfico de gastos** — trocar `self.montarDadosCategorias` inteiro (com o JSDoc) por:

```javascript
    /**
     * Agrupa as saídas do mês selecionado por categoria, pro donut de "Gastos por categoria". As
     * fatias saem da maior pra menor, com a mesma cor do IconTile da categoria.
     *
     * @returns {Array} fatias no formato do DonutChart ({ label, value, color })
     */
    self.montarDadosCategorias = function () {
        var totais = {};

        self.obterTransacoesDoMes(self.state.periodo.mes, self.state.periodo.ano).forEach(function (tx) {
            if (tx.tipo === 'SAIDA') {
                totais[tx.categoriaId] = (totais[tx.categoriaId] || 0) + tx.valor;
            }
        });

        return Object.keys(totais)
            .map(function (chave) {
                var categoriaId = parseInt(chave, 10);
                var categoria = self.getCategoriaById(categoriaId);

                return { label: categoria ? categoria.nome : 'Outro', value: totais[chave], color: self.resolveIconeCategoria(categoriaId).cor };
            })
            .sort(function (a, b) { return b.value - a.value; });
    };
```

- [ ] **Step 5: Chips de filtro** — em `self.buildFilterRow`, trocar o bloco `self.categoriasDisponiveis.forEach(...)` por:

```javascript
        self.state.categorias.forEach(function (categoria) {
            var icone = self.resolveIconeCategoria(categoria.id);
            var $chip = $('<button>', { type: 'button', class: 'ef-tag filter-chip' })
                .attr({ 'data-categoria': categoria.id, 'aria-pressed': String(self.state.categoriasFiltradas.indexOf(categoria.id) !== -1) })
                .append(icones.criar(icone.icone, 'xs'), ' ' + categoria.nome);

            $row.append($chip);
        });
```

e, no handler `$(document).on('click', '.filter-chip', ...)` do `self.iniciar`, trocar o corpo por:

```javascript
                var atributo = $(this).attr('data-categoria');

                if (!atributo) {
                    self.state.categoriasFiltradas = [];
                } else {
                    var categoriaId = parseInt(atributo, 10);
                    var indice = self.state.categoriasFiltradas.indexOf(categoriaId);

                    if (indice === -1) {
                        self.state.categoriasFiltradas.push(categoriaId);
                    } else {
                        self.state.categoriasFiltradas.splice(indice, 1);
                    }
                }

                self.buildFilterRow();
                self.aplicarFiltros();
```

- [ ] **Step 6: Carregamento** — adicionar, logo depois de `self.carregarContas` (depois do fechamento dele):

```javascript
    /**
     * Busca as categorias (fixas + do usuário) e atualiza tudo que depende delas: página
     * Categorias, chips de filtro, lista de recorrências e, se já carregadas, as movimentações e
     * gráficos.
     *
     * @returns
     */
    self.carregarCategorias = function () {
        $.ajax({
            url: self.apiBaseUrl + '/api/categorias',
            headers: self.cabecalhoAuth(),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (resposta) {
                self.state.categorias = resposta;
                self.renderCategorias();
                self.buildFilterRow();
                self.renderRecorrencias();

                if (self.state.transactions.length > 0) {
                    self.aplicarFiltros();
                }
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };

    /**
     * Busca os ícones e tons permitidos pra uma categoria (usados no modal de categoria).
     *
     * @returns
     */
    self.carregarOpcoesCategoria = function () {
        $.ajax({
            url: self.apiBaseUrl + '/api/categorias/opcoes',
            headers: self.cabecalhoAuth(),
            success: function (resposta) {
                self.state.opcoesCategoria = resposta;
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            }
        });
    };
```

e no `self.iniciar`, logo depois de `self.carregarContas();`:

```javascript
            self.carregarCategorias();
            self.carregarOpcoesCategoria();
```

(`self.renderCategorias` é criado na Task 6; até lá, pra `npm run lint:js` passar, a Task 4 pode ser verificada junto com a Task 6 — ver Step 7.)

- [ ] **Step 7: Verificar** — só depois da Task 6 (a Task 4 referencia `self.renderCategorias`). Rodar `npm run lint:js` agora mesmo assim: esperado exit 0 (o ESLint não acusa método de objeto inexistente).

- [ ] **Step 8: Checkpoint.**

---

### Task 5: Front — chips dinâmicos nos modais de movimentação e recorrência

**Files:** `index.html`, `assets/js/script.js`

- [ ] **Step 1: HTML** — em `index.html`, esvaziar as duas fileiras de chips (apagar todos os `<button class="ef-tag categoria-chip" ...>...</button>` de dentro de `#categoriaRow` e de `#categoriaRowRecorrencia`, deixando só as `<div>` vazias).

- [ ] **Step 2: Montagem dos chips** — adicionar, logo antes de `self.applyTypeStyle`:

```javascript
    /**
     * Monta os chips de categoria de um modal com as categorias do tipo informado.
     *
     * @param {string} seletorFileira fileira de chips (#categoriaRow ou #categoriaRowRecorrencia)
     * @param {string} tipo ENTRADA ou SAIDA
     * @param {number|null} categoriaSelecionadaId categoria que já vem marcada
     * @returns
     */
    self.montarChipsCategoria = function (seletorFileira, tipo, categoriaSelecionadaId) {
        var $fileira = $(seletorFileira).empty();

        self.state.categorias
            .filter(function (categoria) { return categoria.tipo === tipo; })
            .forEach(function (categoria) {
                var icone = self.resolveIconeCategoria(categoria.id);

                $fileira.append($('<button>', { type: 'button', class: 'ef-tag categoria-chip' })
                    .attr({ 'data-categoria': categoria.id, 'aria-pressed': String(categoria.id === categoriaSelecionadaId) })
                    .append(icones.criar(icone.icone, 'xs'), ' ' + categoria.nome));
            });
    };

    /**
     * Categoria que já vem marcada ao abrir o modal: na entrada, a fixa "Renda"; na saída, nenhuma
     * (a escolha é obrigatória).
     *
     * @param {string} tipo ENTRADA ou SAIDA
     * @returns {number|null} id da categoria, ou null
     */
    self.categoriaPadraoDoTipo = function (tipo) {
        if (tipo !== 'ENTRADA') {
            return null;
        }

        var renda = self.state.categorias.find(function (categoria) { return categoria.fixa && categoria.tipo === 'ENTRADA'; });
        return renda ? renda.id : null;
    };
```

- [ ] **Step 3: `applyTypeStyle`** — trocar as linhas a partir do comentário `// Categorias só fazem sentido pra saída...` até o fim do método por:

```javascript
        var tipo = isIn ? 'ENTRADA' : 'SAIDA';

        self.state.currentCategoria = self.categoriaPadraoDoTipo(tipo);
        feedback.limparErro('#categoriaRow');
        self.montarChipsCategoria('#categoriaRow', tipo, self.state.currentCategoria);
```

- [ ] **Step 4: `applyRecorrenciaTypeStyle`** — trocar as 5 linhas finais (de `$('#categoriaRowRecorrencia .categoria-chip').attr(...)` até o `.css('display', ...)` do chip RENDA) por:

```javascript
        var tipo = isIn ? 'ENTRADA' : 'SAIDA';

        self.state.currentCategoriaRecorrencia = self.categoriaPadraoDoTipo(tipo);
        feedback.limparErro('#categoriaRowRecorrencia');
        self.montarChipsCategoria('#categoriaRowRecorrencia', tipo, self.state.currentCategoriaRecorrencia);
```

- [ ] **Step 5: Envio** — em `self.criarTransacao`, trocar `categoria: self.state.currentCategoria,` por `categoriaId: self.state.currentCategoria,`; em `self.criarRecorrencia`, trocar `categoria: self.state.currentCategoriaRecorrencia,` por `categoriaId: self.state.currentCategoriaRecorrencia,`. (Os handlers de clique dos chips continuam iguais: `$(this).data('categoria')` já devolve número.)

- [ ] **Step 6: Verificar** — `npm run lint:js` → exit 0; `grep -n "RENDA\|categoriasDisponiveis\|campoCategoria').css" assets/js/script.js` → nada.

- [ ] **Step 7: Checkpoint.**

---

### Task 6: Front — seção "Categorias" e modal de categoria

**Files:** `index.html`, `assets/css/style.css`, `assets/js/script.js`, `sw.js`

- [ ] **Step 1: Navegação** — em `index.html`, depois do `<li>` do item `data-page="recorrencias"` da sidebar:

```html
            <li>
                <button class="ef-nav-item nav-item" data-page="categorias">
                    <span class="ef-nav-item__icon"><i data-lucide="tags" class="ef-icon ef-icon--md"></i></span>
                    <span class="ef-nav-item__label">Categorias</span>
                </button>
            </li>
```

e, na `bottom-nav`, depois do botão `data-page="recorrencias"`, copiar a estrutura dele trocando `data-page` por `categorias`, o ícone por `tags` e o rótulo por `Categorias`.

- [ ] **Step 2: Página** — logo depois do fechamento de `<div class="page" id="pageRecorrencias">…</div>`:

```html
        <div class="page" id="pageCategorias">

            <div class="page-header">
                <div class="page-header-text">
                    <h1 class="page-title">Categorias</h1>
                    <p class="page-subtitle">Organize suas entradas e saídas</p>
                </div>
                <button class="ef-btn ef-btn--primary ef-btn--sm" id="btnNewCategoria">
                    <i data-lucide="plus" class="ef-icon ef-icon--sm"></i>
                    <span class="btn-label">Nova categoria</span>
                </button>
            </div>

            <section class="ef-card ef-card--pad-none categorias-grupo" aria-labelledby="tituloCategoriasEntrada">
                <h2 class="categorias-grupo__titulo" id="tituloCategoriasEntrada">Entradas</h2>
                <ul class="transactions-list" id="categoriasEntrada"></ul>
            </section>

            <section class="ef-card ef-card--pad-none categorias-grupo" aria-labelledby="tituloCategoriasSaida">
                <h2 class="categorias-grupo__titulo" id="tituloCategoriasSaida">Saídas</h2>
                <ul class="transactions-list" id="categoriasSaida"></ul>
            </section>
        </div>
```

- [ ] **Step 3: Modal** — antes de `<!-- MODAL: MEU PERFIL -->`:

```html
    <!-- MODAL: NOVA/EDITAR CATEGORIA -->
    <div class="ef-dialog ef-dialog--auto" id="modalCategoria" role="dialog" aria-modal="true" aria-labelledby="modalCategoriaTitle" hidden>
        <div class="ef-dialog__panel">
            <span class="ef-dialog__handle" aria-hidden="true"></span>
            <div class="ef-dialog__head">
                <div class="ef-dialog__text">
                    <h2 class="ef-dialog__title" id="modalCategoriaTitle">Nova categoria</h2>
                </div>
                <button type="button" class="ef-dialog__close" aria-label="Fechar"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18" /><path d="M6 6l12 12" /></svg></button>
            </div>

            <div class="categoria-previa" aria-hidden="true">
                <span class="ef-icon-tile" id="previaCategoriaTile"></span>
                <span class="categoria-previa__nome" id="previaCategoriaNome">Nova categoria</span>
            </div>

            <div class="ef-field">
                <label class="ef-field__label" for="inputCategoriaNome">Nome</label>
                <input class="ef-input" type="text" id="inputCategoriaNome" maxlength="40" autocomplete="off" placeholder="Ex.: Pet, Salário">
            </div>

            <div class="ef-field">
                <label class="ef-field__label" for="inputCategoriaTipo">Tipo</label>
                <div class="ef-select">
                    <select class="ef-select__control" id="inputCategoriaTipo">
                        <option value="SAIDA">Saída</option>
                        <option value="ENTRADA">Entrada</option>
                    </select>
                    <span class="ef-select__chevron"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg></span>
                </div>
                <span class="ef-field__message" id="dicaCategoriaTipo" hidden>O tipo não muda depois de criada.</span>
            </div>

            <div class="ef-field">
                <span class="ef-field__label" id="rotuloIconeCategoria">Ícone</span>
                <div class="icone-picker" id="iconePickerCategoria" role="group" aria-labelledby="rotuloIconeCategoria"></div>
            </div>

            <div class="ef-field">
                <span class="ef-field__label" id="rotuloTomCategoria">Cor</span>
                <div class="color-picker" id="tomPickerCategoria" role="group" aria-labelledby="rotuloTomCategoria"></div>
            </div>

            <button type="button" class="ef-btn ef-btn--primary ef-btn--lg ef-btn--block" id="btnConfirmCategoria">Salvar categoria</button>
        </div>
    </div>
```

- [ ] **Step 4: CSS** — no fim de `assets/css/style.css`:

```css
/* ===== Categorias ===== */

.categorias-grupo + .categorias-grupo {
    margin-top: var(--space-4);
}

.categorias-grupo__titulo {
    margin: 0;
    padding: var(--space-4) var(--space-5) var(--space-2);
    font: var(--type-caption);
    font-weight: var(--weight-semibold);
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
}

.categoria-fixa {
    display: inline-flex;
    color: var(--text-faint);
}

.categoria-previa {
    display: flex;
    align-items: center;
    gap: var(--space-3);
}

.categoria-previa__nome {
    font: var(--type-body);
    font-weight: var(--weight-semibold);
}

.icone-picker {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(var(--icon-btn-sm), 1fr));
    gap: var(--space-2);
}

.icone-opcao {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: var(--icon-btn-sm);
    padding: 0;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
    background-color: var(--surface-card);
    color: var(--text-secondary);
    cursor: pointer;
}

.icone-opcao[aria-pressed="true"] {
    border-color: var(--border-brand);
    background-color: var(--surface-sunken);
    color: var(--text-primary);
}

.icone-opcao:focus-visible {
    outline: none;
    box-shadow: var(--shadow-focus);
}

#modalCategoria [hidden] {
    display: none;
}
```

- [ ] **Step 5: JS da página e do modal** — adicionar logo depois de `self.carregarOpcoesCategoria` (Task 4):

```javascript
    /**
     * Monta a linha de uma categoria: tile colorido + nome; fixas com cadeado, personalizadas com
     * editar/excluir.
     *
     * @param {object} categoria categoria da API
     * @returns {jQuery} elemento &lt;li&gt;
     */
    self.buildCategoriaItem = function (categoria) {
        var $fim;

        if (categoria.fixa) {
            $fim = $('<span>', { class: 'categoria-fixa', title: 'Categoria do sistema', 'aria-label': 'Categoria do sistema' })
                .append(icones.criar('lock', 'sm'));
        } else {
            var $editar = self.criarBotaoIcone('pencil', 'Editar', false)
                .on('click', function () {
                    self.abrirEdicaoCategoria(categoria);
                });

            var $excluir = self.criarBotaoIcone('trash-2', 'Excluir', true)
                .on('click', function () {
                    self.excluirCategoria(categoria);
                });

            $fim = $('<span>', { class: 'card-item-actions' }).append($editar, $excluir);
        }

        return $('<li>', { class: 'ef-list-row' }).append(
            $('<span>', { class: 'ef-list-row__leading' }).append(self.criarTileCategoria(categoria.id)),
            $('<span>', { class: 'ef-list-row__text' }).append(
                $('<span>', { class: 'ef-list-row__title', text: categoria.nome }),
                $('<span>', { class: 'ef-list-row__subtitle', text: categoria.fixa ? 'Do sistema' : 'Personalizada' })
            ),
            $fim
        );
    };

    self.renderCategorias = function () {
        var $entradas = $('#categoriasEntrada').empty();
        var $saidas = $('#categoriasSaida').empty();

        self.state.categorias.forEach(function (categoria) {
            (categoria.tipo === 'ENTRADA' ? $entradas : $saidas).append(self.buildCategoriaItem(categoria));
        });
    };

    /**
     * Monta a grade de ícones e os tons do modal, marcando os selecionados.
     *
     * @returns
     */
    self.montarSeletoresCategoria = function () {
        var $icones = $('#iconePickerCategoria').empty();
        var $tons = $('#tomPickerCategoria').empty();

        self.state.opcoesCategoria.icones.forEach(function (nomeIcone) {
            $icones.append($('<button>', { type: 'button', class: 'icone-opcao', 'aria-label': nomeIcone, title: nomeIcone })
                .attr({ 'data-icone': nomeIcone, 'aria-pressed': String(nomeIcone === self.state.categoriaIconeSelecionado) })
                .append(icones.criar(nomeIcone, 'sm')));
        });

        self.state.opcoesCategoria.tons.forEach(function (tom) {
            $tons.append($('<button>', { type: 'button', class: 'color-swatch' + (tom === self.state.categoriaTomSelecionado ? ' selected' : ''), 'aria-label': tom })
                .attr({ 'data-tom': tom, 'aria-pressed': String(tom === self.state.categoriaTomSelecionado) })
                .css('background', 'var(--tone-' + tom + '-accent)'));
        });

        self.atualizarPreviaCategoria();
    };

    /**
     * Atualiza a prévia do tile (ícone + tom) e do nome enquanto o usuário edita o modal.
     *
     * @returns
     */
    self.atualizarPreviaCategoria = function () {
        var tom = self.state.categoriaTomSelecionado;

        $('#previaCategoriaTile')
            .attr('class', 'ef-icon-tile' + (tom && tom !== 'neutral' ? ' ef-icon-tile--' + tom : ''))
            .empty()
            .append(icones.criar(self.state.categoriaIconeSelecionado || 'ellipsis', 'sm'));
        $('#previaCategoriaNome').text($('#inputCategoriaNome').val().trim() || 'Nova categoria');
    };

    self.abrirModalNovaCategoria = function () {
        self.state.editingCategoriaId = null;
        self.state.categoriaIconeSelecionado = self.state.opcoesCategoria.icones[5] || 'ellipsis';
        self.state.categoriaTomSelecionado = 'brand';

        $('#modalCategoriaTitle').text('Nova categoria');
        $('#inputCategoriaNome').val('');
        $('#inputCategoriaTipo').val('SAIDA').prop('disabled', false);
        $('#dicaCategoriaTipo').prop('hidden', true);

        self.montarSeletoresCategoria();
        self.openModal('#modalCategoria');
    };

    self.abrirEdicaoCategoria = function (categoria) {
        self.state.editingCategoriaId = categoria.id;
        self.state.categoriaIconeSelecionado = categoria.icone;
        self.state.categoriaTomSelecionado = categoria.tom;

        $('#modalCategoriaTitle').text('Editar categoria');
        $('#inputCategoriaNome').val(categoria.nome);
        $('#inputCategoriaTipo').val(categoria.tipo).prop('disabled', true);
        $('#dicaCategoriaTipo').prop('hidden', false);

        self.montarSeletoresCategoria();
        self.openModal('#modalCategoria');
    };

    /**
     * Cria ou atualiza a categoria do modal. Depois recarrega categorias e movimentações (o nome
     * da categoria aparece na lista do dashboard).
     *
     * @returns
     */
    self.salvarCategoria = function () {
        feedback.limparErros('#modalCategoria');

        var nome = $('#inputCategoriaNome').val().trim();

        if (!nome) {
            feedback.marcarErro('#inputCategoriaNome', 'Informe o nome da categoria');
            feedback.focarPrimeiroErro('#modalCategoria');
            return;
        }

        var editando = self.state.editingCategoriaId !== null;
        var corpo = { nome: nome, icone: self.state.categoriaIconeSelecionado, tom: self.state.categoriaTomSelecionado };

        if (!editando) {
            corpo.tipo = $('#inputCategoriaTipo').val();
        }

        $.ajax({
            url: self.apiBaseUrl + '/api/categorias' + (editando ? '/' + self.state.editingCategoriaId : ''),
            method: editando ? 'PUT' : 'POST',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify(corpo),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function () {
                self.closeModal('#modalCategoria');
                self.carregarCategorias();
                self.carregarTransacoes();
                feedback.exibirSucesso(editando ? 'Categoria atualizada' : 'Categoria criada');
            },
            error: function (jqXHR) {
                var nomeRepetido = feedback.mensagemDaApi(jqXHR, 409);

                if (nomeRepetido) {
                    feedback.marcarErro('#inputCategoriaNome', nomeRepetido);
                    feedback.focarPrimeiroErro('#modalCategoria');
                } else {
                    self.tratarErroRequisicao(jqXHR);
                }
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };

    /**
     * Pede confirmação e exclui uma categoria personalizada. O aviso diz quantas movimentações e
     * recorrências vão pra "Outro"/"Renda" (contadas nos dados já carregados).
     *
     * @param {object} categoria categoria da API
     * @returns
     */
    self.excluirCategoria = function (categoria) {
        var destino = categoria.tipo === 'ENTRADA' ? 'Renda' : 'Outro';
        var emUso = self.state.transactions.filter(function (tx) { return tx.categoriaId === categoria.id; }).length
            + self.state.recorrencias.filter(function (recorrencia) { return recorrencia.categoriaId === categoria.id; }).length;
        var aviso = emUso > 0
            ? emUso + (emUso === 1 ? ' movimentação/recorrência vai' : ' movimentações/recorrências vão') + ' para "' + destino + '".'
            : 'Nenhuma movimentação usa essa categoria.';

        feedback.confirmar({
            titulo: 'Excluir categoria',
            descricao: '"' + categoria.nome + '" será excluída. ' + aviso,
            rotuloConfirmar: 'Excluir categoria',
            aoConfirmar: function () {
                self.executarExclusaoCategoria(categoria.id);
            }
        });
    };

    self.executarExclusaoCategoria = function (id) {
        $.ajax({
            url: self.apiBaseUrl + '/api/categorias/' + id,
            method: 'DELETE',
            headers: self.cabecalhoAuth(),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (resposta) {
                self.state.categoriasFiltradas = self.state.categoriasFiltradas.filter(function (filtrada) { return filtrada !== id; });
                self.carregarCategorias();
                self.carregarTransacoes();
                self.carregarRecorrencias();
                feedback.exibirSucesso('Categoria excluída', resposta.movidas > 0 ? resposta.movidas + ' lançamento(s) movido(s).' : '');
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };
```

- [ ] **Step 6: Ligações no `self.iniciar`** — junto das outras ligações de modal (antes de `self.exibirDadosUsuario();`):

```javascript
            $('#btnNewCategoria').on('click', self.abrirModalNovaCategoria);
            $('#btnConfirmCategoria').on('click', self.salvarCategoria);
            $('#inputCategoriaNome').on('input', self.atualizarPreviaCategoria);

            $('#modalCategoria').on('click', function (e) {
                if ($(e.target).is('#modalCategoria')) {
                    self.closeModal('#modalCategoria');
                }
            });

            $(document).on('click', '#iconePickerCategoria .icone-opcao', function () {
                self.state.categoriaIconeSelecionado = $(this).attr('data-icone');
                $('#iconePickerCategoria .icone-opcao').attr('aria-pressed', 'false');
                $(this).attr('aria-pressed', 'true');
                self.atualizarPreviaCategoria();
            });

            $(document).on('click', '#tomPickerCategoria .color-swatch', function () {
                self.state.categoriaTomSelecionado = $(this).attr('data-tom');
                $('#tomPickerCategoria .color-swatch').removeClass('selected').attr('aria-pressed', 'false');
                $(this).addClass('selected').attr('aria-pressed', 'true');
                self.atualizarPreviaCategoria();
            });
```

- [ ] **Step 7: Conferir `self.navigateTo`** — ele troca de página por `'#page' + capitalize(page)`, então `data-page="categorias"` já abre `#pageCategorias` sem mudança. Confirmar lendo o método.

- [ ] **Step 8: PWA** — em `sw.js`, trocar o comentário e a constante do cache por:

```javascript
// v6: categorias dinâmicas (seção Categorias, chips e gráfico vindos da API). Trocar a versão
// descarta o cache antigo, que serviria o CSS/JS anterior (cache-first).
const CACHE_NAME = 'e-financeiro-shell-v6';
```

- [ ] **Step 9: Verificar** — `npm run check` → exit 0 (0 erros; os avisos do `lint:design` já existiam). Conferir com `grep` que todo id usado no JS novo existe uma vez no `index.html`.

- [ ] **Step 10: Checkpoint.**

---

### Task 7: Teste de ponta a ponta (feito pelo coordenador) e Obsidian

- [ ] **Step 1:** Backend local (`./gradlew bootRun` contra um Postgres em Docker) + cópia do front apontando pra `http://localhost:8080`, servida na porta 5501. Roteiro: cadastrar; criar categoria de saída "Pet" e de entrada "Salário"; lançar uma saída com "Pet" e uma entrada com "Salário" (Renda vem marcada na entrada); conferir tile/nome na lista, chip de filtro e fatia do gráfico; editar "Pet" (nome/ícone/cor) e ver refletir na lista; excluir "Pet" e conferir que a movimentação passou pra "Outro" e o Toast; conferir que fixas têm cadeado e sem ações; conferir que o tipo fica travado na edição.
- [ ] **Step 2:** Registrar no Obsidian (`Decisoes.md` back e front, `Visao-Geral.md`).
