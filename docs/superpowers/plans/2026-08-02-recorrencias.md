# Recorrências (parcelas e gastos/entradas fixas) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Desvios do template padrão desta skill, pela mesma convenção já usada no plano anterior (`2026-08-01-contas-dinamicas.md`):**
> 1. **Sem testes automatizados.** Nenhuma tarefa escreve testes — o projeto não tem suíte de testes por decisão explícita do usuário. Cada tarefa termina com uma seção **Verificação** (curl / navegador) em vez do ciclo TDD padrão.
> 2. **Nunca commitar automaticamente.** O usuário sempre commita ele mesmo. Cada tarefa termina com um checkpoint "**Pare aqui**" sugerindo uma mensagem de commit, mas nenhum passo deste plano deve rodar `git commit`.

**Goal:** Permitir cadastrar de uma vez uma compra parcelada ou um gasto/entrada fixa (aluguel, assinatura, salário), gerando todas as parcelas como transações reais no ato do cadastro, com a possibilidade de cancelar as parcelas futuras ou editar o valor das futuras depois.

**Architecture:** Nova entidade `Recorrencia` (descrição, valor por parcela, tipo, categoria, conta, cartão opcional, quantidade de parcelas, data de início). No cadastro, o backend gera de uma vez todas as `Transacao` (uma por mês a partir da data de início), cada uma vinculada à `Recorrencia` por uma FK opcional. Duas ações em lote sobre parcelas futuras (`dataTransacao >= hoje`): cancelar (apaga) e editar valor (propaga). Nenhum job/cron — tudo é gerado de uma vez no cadastro, porque o Render free tier dorme quando ocioso e não rodaria um `@Scheduled` de forma confiável.

**Tech Stack:** Java 17 / Spring Boot / Spring Data JPA / Flyway / PostgreSQL (backend, `D:\PROJETOS\e-financeiro`); HTML/CSS/jQuery puro (frontend, `D:\PROJETOS\e-financeiro-front`).

**Spec:** `docs/superpowers/specs/2026-08-02-recorrencias-design.md`

---

## Pré-requisitos pra rodar a verificação

```bash
cd /d/PROJETOS/e-financeiro
export DB_URL="..." DB_USUARIO="..." DB_SENHA="..." JWT_SECRET="..."
./gradlew bootRun
```

```bash
cd /d/PROJETOS/e-financeiro-front
npx --yes http-server -p 5501 .
```

(Porta **5501**, não 5502 — é a origem que o backend já libera por padrão em `CORS_ORIGENS_PERMITIDAS`. Usar `http-server`, não `serve`, que faz redirect de clean-urls e derruba query strings.)

---

## Backend

### Task 1: Entidade `Recorrencia` + migration + campo em `Transacao`

**Files:**
- Create: `src/main/java/com/efinanceiro/dominio/Recorrencia.java`
- Create: `src/main/resources/db/migration/V5__recorrencias.sql`
- Modify: `src/main/java/com/efinanceiro/dominio/Transacao.java`

- [ ] **Step 1: Migration**

```sql
CREATE TABLE recorrencias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    conta_id BIGINT NOT NULL REFERENCES contas (id),
    cartao_id BIGINT REFERENCES cartoes (id) ON DELETE SET NULL,
    descricao VARCHAR(160) NOT NULL,
    valor NUMERIC(12, 2) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    total_parcelas INT NOT NULL,
    data_inicio DATE NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_recorrencias_usuario_id ON recorrencias (usuario_id);
CREATE INDEX idx_recorrencias_conta_id ON recorrencias (conta_id);
CREATE INDEX idx_recorrencias_cartao_id ON recorrencias (cartao_id);

ALTER TABLE transacoes ADD COLUMN recorrencia_id BIGINT REFERENCES recorrencias (id) ON DELETE SET NULL;

CREATE INDEX idx_transacoes_recorrencia_id ON transacoes (recorrencia_id);
```

- [ ] **Step 2: Criar a entidade `Recorrencia`**

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recorrencias")
@Getter
@Setter
@NoArgsConstructor
public class Recorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 160)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @Column(name = "total_parcelas", nullable = false)
    private Integer totalParcelas;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        this.criadoEm = Instant.now();
    }
}
```

- [ ] **Step 3: Adicionar o campo `recorrencia` em `Transacao`**

Trocar:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @Column(name = "data_transacao", nullable = false)
    private LocalDate dataTransacao;
```

por:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorrencia_id")
    private Recorrencia recorrencia;

    @Column(name = "data_transacao", nullable = false)
    private LocalDate dataTransacao;
```

(`Recorrencia` está no mesmo pacote `com.efinanceiro.dominio`, não precisa de import novo. Campo opcional — `optional = true` é o padrão do `@ManyToOne`, igual `cartao`.)

- [ ] **Step 4: Verificação**

```bash
./gradlew compileJava
```

Esperado: `BUILD SUCCESSFUL`. (Hibernate só vai validar o schema de verdade quando o app subir com banco — isso acontece na Task 3, depois que a API existir de fato pra testar com curl.)

- [ ] **Step 5: Pare aqui**

Não commite. Sugestão de mensagem:

```
adiciona entidade Recorrencia e migration
```

---

### Task 2: DTOs e repositórios

**Files:**
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoRecorrencia.java`
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoAtualizacaoValorRecorrencia.java`
- Create: `src/main/java/com/efinanceiro/dto/resposta/RespostaRecorrencia.java`
- Create: `src/main/java/com/efinanceiro/repositorio/RepositorioRecorrencia.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioTransacao.java`

- [ ] **Step 1: Criar `RequisicaoRecorrencia`**

```java
package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RequisicaoRecorrencia(

        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor,

        @NotNull(message = "O tipo (entrada ou saída) é obrigatório")
        TipoTransacao tipo,

        @NotNull(message = "A categoria é obrigatória")
        Categoria categoria,

        @NotNull(message = "A conta é obrigatória")
        Long contaId,

        Long cartaoId,

        @NotNull(message = "A quantidade de parcelas é obrigatória")
        @Min(value = 1, message = "A quantidade de parcelas deve ser no mínimo 1")
        @Max(value = 360, message = "A quantidade de parcelas deve ser no máximo 360")
        Integer totalParcelas,

        LocalDate dataInicio
)
{}
```

- [ ] **Step 2: Criar `RequisicaoAtualizacaoValorRecorrencia`**

```java
package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RequisicaoAtualizacaoValorRecorrencia(

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        BigDecimal valor
)
{}
```

- [ ] **Step 3: Criar `RespostaRecorrencia`**

```java
package com.efinanceiro.dto.resposta;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaRecorrencia(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Categoria categoria,
        Long contaId,
        String nomeConta,
        Long cartaoId,
        String nomeCartao,
        Integer totalParcelas,
        Integer parcelasRestantes,
        LocalDate dataInicio
) {
}
```

- [ ] **Step 4: Criar `RepositorioRecorrencia`**

```java
package com.efinanceiro.repositorio;

import com.efinanceiro.dominio.Recorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioRecorrencia extends JpaRepository<Recorrencia, Long> {

    /**
     * Lista todas as recorrências pertencentes a um usuário.
     *
     * @param usuarioId Id do usuário dono das recorrências
     * @return Lista de recorrências do usuário
     */
    List<Recorrencia> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma recorrência pelo id, garantindo que pertence ao usuário informado.
     *
     * @param id Id da recorrência
     * @param usuarioId Id do usuário dono da recorrência
     * @return Recorrência encontrada, se existir e pertencer ao usuário
     */
    Optional<Recorrencia> findByIdAndUsuarioId(Long id, Long usuarioId);
}
```

- [ ] **Step 5: Adicionar métodos em `RepositorioTransacao`**

Adicionar ao final da interface (antes do `}` de fechamento), depois do método `deleteByContaId` já existente:

```java

    /**
     * Conta quantas transações de uma recorrência ainda estão no futuro (não passaram) —
     * usado pra calcular "parcelas restantes".
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — conta transações com data >= essa
     * @return Quantidade de transações futuras da recorrência
     */
    long countByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);

    /**
     * Lista as transações futuras de uma recorrência — usado pra propagar edição de valor.
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — lista transações com data >= essa
     * @return Lista de transações futuras da recorrência
     */
    List<Transacao> findByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);

    /**
     * Apaga as transações futuras de uma recorrência — usado ao cancelar parcelas futuras.
     *
     * @param recorrenciaId Id da recorrência
     * @param data Data de referência (normalmente hoje) — apaga transações com data >= essa
     */
    void deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual(Long recorrenciaId, LocalDate data);
```

(`LocalDate` já está importado no arquivo — usado pelo `findByCartaoIdAndTipoAndDataTransacaoBetween` que já existe.)

- [ ] **Step 6: Verificação**

```bash
./gradlew compileJava
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Pare aqui**

Não commite. Sugestão de mensagem:

```
adiciona DTOs e repositório de Recorrencia
```

---

### Task 3: Serviço e controlador

**Files:**
- Create: `src/main/java/com/efinanceiro/servico/ServicoRecorrencia.java`
- Create: `src/main/java/com/efinanceiro/controlador/ControladorRecorrencia.java`

- [ ] **Step 1: Criar `ServicoRecorrencia`**

```java
package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Recorrencia;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoRecorrencia;
import com.efinanceiro.dto.resposta.RespostaRecorrencia;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ServicoRecorrencia {

    private final RepositorioRecorrencia repositorioRecorrencia;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioConta repositorioConta;
    private final RepositorioCartao repositorioCartao;

    public ServicoRecorrencia(RepositorioRecorrencia repositorioRecorrencia,
                               RepositorioTransacao repositorioTransacao,
                               RepositorioUsuario repositorioUsuario,
                               RepositorioConta repositorioConta,
                               RepositorioCartao repositorioCartao) {
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioConta = repositorioConta;
        this.repositorioCartao = repositorioCartao;
    }

    /**
     * Lista as recorrências do usuário autenticado, cada uma com a quantidade de parcelas
     * futuras ainda restantes.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de recorrências do usuário
     */
    @Transactional(readOnly = true)
    public List<RespostaRecorrencia> listarRecorrencias(String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioRecorrencia.findByUsuarioId(usuario.getId()).stream()
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Cria uma nova recorrência para o usuário autenticado e já gera todas as transações
     * (uma por mês, a partir da data de início).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da recorrência
     * @return Recorrência criada
     */
    public RespostaRecorrencia criarRecorrencia(String emailUsuario, RequisicaoRecorrencia requisicao) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Conta conta = resolverConta(requisicao.contaId(), usuario.getId());
        Cartao cartao = resolverCartao(requisicao.cartaoId(), usuario.getId());
        LocalDate dataInicio = requisicao.dataInicio() != null ? requisicao.dataInicio() : LocalDate.now();

        Recorrencia recorrencia = new Recorrencia();
        recorrencia.setUsuario(usuario);
        recorrencia.setDescricao(requisicao.descricao());
        recorrencia.setValor(requisicao.valor());
        recorrencia.setTipo(requisicao.tipo());
        recorrencia.setCategoria(requisicao.categoria());
        recorrencia.setConta(conta);
        recorrencia.setCartao(cartao);
        recorrencia.setTotalParcelas(requisicao.totalParcelas());
        recorrencia.setDataInicio(dataInicio);
        repositorioRecorrencia.save(recorrencia);

        for (int i = 0; i < requisicao.totalParcelas(); i++) {
            Transacao transacao = new Transacao();
            transacao.setUsuario(usuario);
            transacao.setDescricao(requisicao.descricao());
            transacao.setValor(requisicao.valor());
            transacao.setTipo(requisicao.tipo());
            transacao.setCategoria(requisicao.categoria());
            transacao.setConta(conta);
            transacao.setCartao(cartao);
            transacao.setRecorrencia(recorrencia);
            transacao.setDataTransacao(dataInicio.plusMonths(i));
            repositorioTransacao.save(transacao);
        }

        return paraResposta(recorrencia);
    }

    /**
     * Cancela as parcelas futuras de uma recorrência (apaga as transações com data de hoje
     * em diante). As parcelas passadas e o registro da recorrência não são apagados.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     */
    public void cancelarFuturas(String emailUsuario, Long id) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);
        repositorioTransacao.deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());
    }

    /**
     * Atualiza o valor das parcelas futuras de uma recorrência (a partir de hoje) e o valor
     * de referência da recorrência. As parcelas passadas não mudam.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da recorrência
     * @param novoValor Novo valor a aplicar nas parcelas futuras
     * @return Recorrência atualizada
     */
    public RespostaRecorrencia atualizarValorFuturo(String emailUsuario, Long id, BigDecimal novoValor) {
        Recorrencia recorrencia = buscarRecorrenciaDoUsuario(emailUsuario, id);

        List<Transacao> futuras = repositorioTransacao
                .findByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());

        if (!futuras.isEmpty()) {
            futuras.forEach(transacao -> transacao.setValor(novoValor));
            repositorioTransacao.saveAll(futuras);

            recorrencia.setValor(novoValor);
            repositorioRecorrencia.save(recorrencia);
        }

        return paraResposta(recorrencia);
    }

    private Conta resolverConta(Long contaId, Long usuarioId) {
        return repositorioConta.findByIdAndUsuarioId(contaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }

    private Cartao resolverCartao(Long cartaoId, Long usuarioId) {
        if (cartaoId == null) {
            return null;
        }

        return repositorioCartao.findByIdAndUsuarioId(cartaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));
    }

    private Recorrencia buscarRecorrenciaDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);

        return repositorioRecorrencia.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"));
    }

    private Usuario buscarUsuario(String email) {
        return repositorioUsuario.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private RespostaRecorrencia paraResposta(Recorrencia recorrencia) {
        Cartao cartao = recorrencia.getCartao();
        int parcelasRestantes = (int) repositorioTransacao
                .countByRecorrenciaIdAndDataTransacaoGreaterThanEqual(recorrencia.getId(), LocalDate.now());

        return new RespostaRecorrencia(
                recorrencia.getId(),
                recorrencia.getDescricao(),
                recorrencia.getValor(),
                recorrencia.getTipo(),
                recorrencia.getCategoria(),
                recorrencia.getConta().getId(),
                recorrencia.getConta().getNome(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                recorrencia.getTotalParcelas(),
                parcelasRestantes,
                recorrencia.getDataInicio()
        );
    }
}
```

- [ ] **Step 2: Criar `ControladorRecorrencia`**

```java
package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoValorRecorrencia;
import com.efinanceiro.dto.requisicao.RequisicaoRecorrencia;
import com.efinanceiro.dto.resposta.RespostaRecorrencia;
import com.efinanceiro.servico.ServicoRecorrencia;
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
@RequestMapping("/api/recorrencias")
public class ControladorRecorrencia {

    private final ServicoRecorrencia servicoRecorrencia;

    public ControladorRecorrencia(ServicoRecorrencia servicoRecorrencia) {
        this.servicoRecorrencia = servicoRecorrencia;
    }

    /**
     * Lista as recorrências do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual, injetada pelo Spring Security
     * @return Lista de recorrências
     */
    @GetMapping
    public ResponseEntity<List<RespostaRecorrencia>> listarRecorrencias(Authentication autenticacao) {
        return ResponseEntity.ok(servicoRecorrencia.listarRecorrencias(autenticacao.getName()));
    }

    /**
     * Cria uma nova recorrência para o usuário autenticado e gera todas as parcelas.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Dados da recorrência
     * @return Recorrência criada
     */
    @PostMapping
    public ResponseEntity<RespostaRecorrencia> criarRecorrencia(Authentication autenticacao,
                                                                  @Valid @RequestBody RequisicaoRecorrencia requisicao) {
        RespostaRecorrencia resposta = servicoRecorrencia.criarRecorrencia(autenticacao.getName(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    /**
     * Cancela as parcelas futuras de uma recorrência do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da recorrência
     * @return Resposta vazia com status 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarFuturas(Authentication autenticacao, @PathVariable Long id) {
        servicoRecorrencia.cancelarFuturas(autenticacao.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Atualiza o valor das parcelas futuras de uma recorrência do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param id Id da recorrência
     * @param requisicao Novo valor a aplicar nas parcelas futuras
     * @return Recorrência atualizada
     */
    @PutMapping("/{id}/valor")
    public ResponseEntity<RespostaRecorrencia> atualizarValorFuturo(Authentication autenticacao,
                                                                      @PathVariable Long id,
                                                                      @Valid @RequestBody RequisicaoAtualizacaoValorRecorrencia requisicao) {
        return ResponseEntity.ok(servicoRecorrencia.atualizarValorFuturo(autenticacao.getName(), id, requisicao.valor()));
    }
}
```

- [ ] **Step 3: Verificação**

Suba o app (ver Pré-requisitos). Cadastre um usuário (ou reaproveite um token existente — precisa de pelo menos uma conta, que todo cadastro já cria automaticamente):

```bash
curl -s -X POST http://localhost:8080/api/autenticacao/cadastro \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste Recorrencia","email":"teste-recorrencia@example.com","senha":"senha1234"}'
```

```bash
export TOKEN="<cole o token aqui>"
curl -s http://localhost:8080/api/contas -H "Authorization: Bearer $TOKEN"
```

Anote o `id` da conta "Pessoal" retornada (normalmente `1` num usuário novo) e use no lugar de `<contaId>` abaixo.

```bash
curl -s -X POST http://localhost:8080/api/recorrencias \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"descricao":"Aluguel","valor":1200.00,"tipo":"SAIDA","categoria":"MORADIA","contaId":<contaId>,"totalParcelas":3}'
```

Esperado: `201`, com `"totalParcelas":3,"parcelasRestantes":3` (as 3 parcelas geradas — hoje, +1 mês, +2 meses — são todas hoje ou no futuro).

```bash
curl -s http://localhost:8080/api/recorrencias -H "Authorization: Bearer $TOKEN"
```

Esperado: lista com a recorrência criada.

```bash
curl -s http://localhost:8080/api/transacoes/resumo -H "Authorization: Bearer $TOKEN"
```

Esperado: `totalSaidas` reflete pelo menos a primeira parcela (as parcelas futuras, com `dataTransacao` no futuro, não entram no resumo se ele filtrar por período — mas todas as 3 já existem como transações; o resumo default não filtra por mês, então `totalSaidas` deve ser `3600.00` = 3 × 1200).

```bash
curl -s -X PUT http://localhost:8080/api/recorrencias/1/valor \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"valor":1350.00}'
```

Esperado: `200`, `"valor":1350.00`.

```bash
curl -s -X DELETE http://localhost:8080/api/recorrencias/1 -H "Authorization: Bearer $TOKEN"
```

Esperado: `204`, sem corpo.

```bash
curl -s http://localhost:8080/api/recorrencias -H "Authorization: Bearer $TOKEN"
```

Esperado: `"parcelasRestantes":0` na recorrência (todas as 3 parcelas eram hoje/futuras, então o cancelamento apagou todas).

- [ ] **Step 4: Pare aqui**

Não commite. Sugestão de mensagem:

```
adiciona ServicoRecorrencia e ControladorRecorrencia
```

---

## Frontend

### Task 4: `index.html` — nova página "Recorrências" e modais

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\index.html`

- [ ] **Step 1: Adicionar "Recorrências" na navegação (sidebar e bottom-nav)**

Trocar:

```html
        <nav class="sidebar-nav">
            <button class="nav-item active" data-page="dashboard">
                <i class="fa-solid fa-house"></i>
                <span>Dashboard</span>
            </button>
            <button class="nav-item" data-page="cards">
                <i class="fa-solid fa-credit-card"></i>
                <span>Cartões</span>
            </button>
            <button class="nav-item" data-page="contas">
                <i class="fa-solid fa-wallet"></i>
                <span>Contas</span>
            </button>
        </nav>
```

por:

```html
        <nav class="sidebar-nav">
            <button class="nav-item active" data-page="dashboard">
                <i class="fa-solid fa-house"></i>
                <span>Dashboard</span>
            </button>
            <button class="nav-item" data-page="cards">
                <i class="fa-solid fa-credit-card"></i>
                <span>Cartões</span>
            </button>
            <button class="nav-item" data-page="contas">
                <i class="fa-solid fa-wallet"></i>
                <span>Contas</span>
            </button>
            <button class="nav-item" data-page="recorrencias">
                <i class="fa-solid fa-rotate"></i>
                <span>Recorrências</span>
            </button>
        </nav>
```

Trocar:

```html
    <nav class="bottom-nav">
        <button class="bottom-nav-item active" data-page="dashboard">
            <i class="fa-solid fa-house"></i>
            <span>Início</span>
        </button>
        <button class="bottom-nav-item" data-page="cards">
            <i class="fa-solid fa-credit-card"></i>
            <span>Cartões</span>
        </button>
        <button class="bottom-nav-item" data-page="contas">
            <i class="fa-solid fa-wallet"></i>
            <span>Contas</span>
        </button>
    </nav>
```

por:

```html
    <nav class="bottom-nav">
        <button class="bottom-nav-item active" data-page="dashboard">
            <i class="fa-solid fa-house"></i>
            <span>Início</span>
        </button>
        <button class="bottom-nav-item" data-page="cards">
            <i class="fa-solid fa-credit-card"></i>
            <span>Cartões</span>
        </button>
        <button class="bottom-nav-item" data-page="contas">
            <i class="fa-solid fa-wallet"></i>
            <span>Contas</span>
        </button>
        <button class="bottom-nav-item" data-page="recorrencias">
            <i class="fa-solid fa-rotate"></i>
            <span>Recorrências</span>
        </button>
    </nav>
```

(`navigateTo()` em `script.js` já lida com qualquer `data-page` genericamente.)

- [ ] **Step 2: Adicionar a página "Recorrências"**

Depois do bloco `<!-- CONTAS --> ... </div>` (fechamento de `#pageContas`) e antes de `<button class="fab-mobile" id="btnFabMobile">`, adicionar:

```html
        <!-- RECORRÊNCIAS -->
        <div class="page" id="pageRecorrencias">

            <div class="page-header">
                <div class="page-header-text">
                    <h1 class="page-title">Recorrências</h1>
                    <p class="page-subtitle">Parcelas e gastos ou entradas fixas</p>
                </div>
                <button class="fab-button" id="btnNewRecorrencia">
                    <i class="fa-solid fa-plus"></i>
                    <span>Nova recorrência</span>
                </button>
            </div>

            <ul class="transactions-list" id="recorrenciasLista"></ul>
        </div>
```

- [ ] **Step 3: Adicionar os modais de recorrência**

Depois do bloco `<!-- MODAL: CONFIRMAR EXCLUSÃO DE CONTA --> ... </div>` (fechamento de `#modalDeleteConta`) e antes das tags `<script>` de jQuery/`script.js`, adicionar:

```html
    <!-- MODAL: NOVA RECORRÊNCIA -->
    <div class="modal-backdrop" id="modalRecorrencia">
        <div class="modal-sheet">
            <div class="modal-handle"></div>
            <h2 class="modal-title">Nova recorrência</h2>

            <div class="type-row">
                <button class="type-btn active-in" id="btnRecorrenciaTypeIn" data-type="in">
                    <i class="fa-solid fa-arrow-down-left"></i> Entrada
                </button>
                <button class="type-btn" id="btnRecorrenciaTypeOut" data-type="out">
                    <i class="fa-solid fa-arrow-up-right"></i> Saída
                </button>
            </div>

            <input class="modal-input" type="text" id="inputRecorrenciaDescription" placeholder="Descrição (ex: Aluguel, Geladeira 10x)">
            <input class="modal-input" type="text" id="inputRecorrenciaValue" placeholder="Valor por parcela (R$)" inputmode="decimal">
            <select class="modal-input" id="inputRecorrenciaAccount"></select>

            <div class="categoria-row" id="categoriaRowRecorrencia">
                <button class="categoria-chip" data-categoria="RENDA" type="button">
                    <i class="fa-solid fa-arrow-down-left"></i> Renda
                </button>
                <button class="categoria-chip" data-categoria="DESPESA" type="button">
                    <i class="fa-solid fa-arrow-up-right"></i> Despesa
                </button>
                <button class="categoria-chip" data-categoria="ALIMENTACAO" type="button">
                    <i class="fa-solid fa-bag-shopping"></i> Alimentação
                </button>
                <button class="categoria-chip" data-categoria="MORADIA" type="button">
                    <i class="fa-solid fa-house"></i> Moradia
                </button>
                <button class="categoria-chip" data-categoria="OUTRO" type="button">
                    <i class="fa-solid fa-ellipsis"></i> Outro
                </button>
            </div>

            <div class="card-row" id="cardRowRecorrencia">
                <select class="modal-input" id="inputRecorrenciaCard">
                    <option value="">Sem cartão (débito / dinheiro)</option>
                </select>
            </div>

            <input class="modal-input" type="number" id="inputRecorrenciaParcelas" placeholder="Quantidade de parcelas" min="1">
            <input class="modal-input" type="date" id="inputRecorrenciaDataInicio" placeholder="Data de início">

            <button class="modal-confirm" id="btnConfirmRecorrencia">Salvar recorrência</button>
        </div>
    </div>

    <!-- MODAL: EDITAR VALOR DA RECORRÊNCIA -->
    <div class="modal-backdrop" id="modalEditRecorrenciaValue">
        <div class="modal-sheet">
            <div class="modal-handle"></div>
            <h2 class="modal-title">Editar valor</h2>

            <p class="auth-help-text">As parcelas passadas não mudam — só as futuras recebem o novo valor.</p>

            <input class="modal-input" type="text" id="inputRecorrenciaNewValue" placeholder="Novo valor (R$)" inputmode="decimal">

            <button class="modal-confirm" id="btnConfirmEditRecorrenciaValue">Salvar novo valor</button>
        </div>
    </div>
```

- [ ] **Step 4: Verificação**

Abra `index.html` num navegador (o `script.js` ainda está no estado antigo — os botões novos não funcionam até a Task 5, e a página fica vazia, o que é esperado nesse ponto). Confirme que:
- A página carrega sem erro de HTML quebrado.
- "Recorrências" aparece na sidebar e no bottom-nav, e navegar até ela mostra a página vazia (`#recorrenciasLista` sem conteúdo).

- [ ] **Step 5: Pare aqui**

Não commite. Sugestão de mensagem:

```
adiciona página Recorrências e modais no index.html
```

---

### Task 5: `script.js` — lógica de recorrências

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\script.js`

- [ ] **Step 1: Ampliar `self.state`**

Trocar:

```js
    self.state = {
        currentPage: 'dashboard',
        currentView: 'all',
        currentType: 'in',
        currentCategoria: null,
        selectedColor: self.cardColors[0],
        selectedContaColor: self.cardColors[0],
        editingContaId: null,
        excludingContaId: null,

        // Filtros do dashboard: mês/ano exibido e categorias marcadas
        // (lista vazia = todas as categorias)
        periodo: { mes: new Date().getMonth(), ano: new Date().getFullYear() },
        categoriasFiltradas: [],

        cards: [],
        contas: [],
        transactions: []
    };
```

por:

```js
    self.state = {
        currentPage: 'dashboard',
        currentView: 'all',
        currentType: 'in',
        currentCategoria: null,
        selectedColor: self.cardColors[0],
        selectedContaColor: self.cardColors[0],
        editingContaId: null,
        excludingContaId: null,
        currentTypeRecorrencia: 'in',
        currentCategoriaRecorrencia: null,
        editingValorRecorrenciaId: null,

        // Filtros do dashboard: mês/ano exibido e categorias marcadas
        // (lista vazia = todas as categorias)
        periodo: { mes: new Date().getMonth(), ano: new Date().getFullYear() },
        categoriasFiltradas: [],

        cards: [],
        contas: [],
        transactions: [],
        recorrencias: []
    };
```

- [ ] **Step 2: Escopar os handlers de `.categoria-chip` existentes e adicionar o da recorrência**

Trocar:

```js
            $(document).on('click', '.categoria-chip', function () {
                self.state.currentCategoria = $(this).data('categoria');
                $('.categoria-chip').removeClass('active');
                $(this).addClass('active');
            });
```

por:

```js
            $(document).on('click', '#categoriaRow .categoria-chip', function () {
                self.state.currentCategoria = $(this).data('categoria');
                $('#categoriaRow .categoria-chip').removeClass('active');
                $(this).addClass('active');
            });

            $(document).on('click', '#categoriaRowRecorrencia .categoria-chip', function () {
                self.state.currentCategoriaRecorrencia = $(this).data('categoria');
                $('#categoriaRowRecorrencia .categoria-chip').removeClass('active');
                $(this).addClass('active');
            });
```

- [ ] **Step 3: Adicionar as funções de recorrência**

Logo depois de `self.excluirConta` (antes do fechamento da função `Dashboard` ou de onde `self.iniciar` começa — procure o próximo `self.` depois de `excluirConta` pra saber onde parar), adicionar:

```js
    /**
     * Busca as recorrências do usuário e atualiza a lista da página Recorrências.
     *
     * @returns
     */
    self.carregarRecorrencias = function () {
        $.ajax({
            url: self.apiBaseUrl + '/api/recorrencias',
            headers: self.cabecalhoAuth(),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (resposta) {
                self.state.recorrencias = resposta;
                self.renderRecorrencias();
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };

    self.buildRecorrenciaItem = function (recorrencia) {
        var icone = self.resolveIconeCategoria(recorrencia.categoria);
        var $icon = $('<div>', { class: 'tx-icon ' + icone.classe }).append($('<i>', { class: 'fa-solid ' + icone.icone }));

        var decorridas = recorrencia.totalParcelas - recorrencia.parcelasRestantes;
        var metaCartao = recorrencia.nomeCartao ? ' · ' + recorrencia.nomeCartao : '';
        var metaText = decorridas + ' de ' + recorrencia.totalParcelas + ' · ' + recorrencia.nomeConta + metaCartao;

        var amountClass = recorrencia.tipo === 'ENTRADA' ? 'tx-amount--in' : 'tx-amount--out';
        var prefix = recorrencia.tipo === 'ENTRADA' ? '+' : '-';

        var $editar = $('<button>', { class: 'item-edit-btn', title: 'Editar valor' })
            .append($('<i>', { class: 'fa-solid fa-pen' }))
            .on('click', function () {
                self.abrirModalEditarValorRecorrencia(recorrencia);
            });

        var $cancelar = $('<button>', { class: 'item-delete-btn', title: 'Cancelar parcelas futuras' })
            .append($('<i>', { class: 'fa-solid fa-trash' }))
            .on('click', function () {
                self.cancelarRecorrenciaFuturas(recorrencia.id);
            });

        return $('<li>', { class: 'tx-item' }).append(
            $icon,
            $('<div>', { class: 'tx-info' }).append(
                $('<p>', { class: 'tx-name', text: recorrencia.descricao }),
                $('<p>', { class: 'tx-meta', text: metaText })
            ),
            $('<span>', { class: 'tx-amount ' + amountClass, text: prefix + self.formatCurrency(recorrencia.valor) }),
            $('<div>', { class: 'card-item-actions' }).append($editar, $cancelar)
        );
    };

    self.renderRecorrencias = function () {
        var $lista = $('#recorrenciasLista').empty();

        if (self.state.recorrencias.length === 0) {
            $lista.append(
                $('<li>', { class: 'tx-empty', text: 'Nenhuma recorrência cadastrada ainda.' })
            );
        } else {
            self.state.recorrencias.forEach(function (recorrencia) {
                $lista.append(self.buildRecorrenciaItem(recorrencia));
            });
        }
    };

    self.populateRecorrenciaAccountSelect = function () {
        var $select = $('#inputRecorrenciaAccount').empty();

        self.state.contas.forEach(function (conta) {
            $select.append($('<option>', { value: conta.id, text: conta.nome }));
        });
    };

    self.populateRecorrenciaCardSelect = function () {
        var $select = $('#inputRecorrenciaCard').empty();
        $select.append($('<option>', { value: '', text: 'Sem cartão (débito / dinheiro)' }));

        self.state.cards.forEach(function (card) {
            $select.append($('<option>', { value: card.id, text: card.nome }));
        });
    };

    self.applyRecorrenciaTypeStyle = function (type) {
        var isIn = type === 'in';

        $('#btnRecorrenciaTypeIn').toggleClass('active-in', isIn).removeClass('active-out');
        $('#btnRecorrenciaTypeOut').toggleClass('active-out', !isIn).removeClass('active-in');
        $('#cardRowRecorrencia').toggleClass('visible', !isIn);

        $('#categoriaRowRecorrencia .categoria-chip').removeClass('active');
        self.state.currentCategoriaRecorrencia = isIn ? 'RENDA' : null;
        $('#categoriaRowRecorrencia').css('display', isIn ? 'none' : 'flex');
        $('#categoriaRowRecorrencia .categoria-chip[data-categoria="RENDA"]').css('display', isIn ? '' : 'none');
    };

    self.resetRecorrenciaModal = function () {
        self.state.currentTypeRecorrencia = 'in';
        $('#inputRecorrenciaDescription').val('');
        $('#inputRecorrenciaValue').val('');
        $('#inputRecorrenciaParcelas').val('');
        $('#inputRecorrenciaDataInicio').val('');
        $('#inputRecorrenciaCard').val('');
        self.populateRecorrenciaAccountSelect();
        self.populateRecorrenciaCardSelect();

        var contaPadrao = self.state.currentView !== 'all' ? self.state.currentView : (self.state.contas[0] ? self.state.contas[0].id : '');
        $('#inputRecorrenciaAccount').val(contaPadrao);

        self.applyRecorrenciaTypeStyle('in');
    };

    self.validateRecorrencia = function (description, value, totalParcelas) {
        var valido = true;

        if (!description) {
            alert('Informe uma descrição.');
            valido = false;
        } else if (isNaN(value) || value <= 0) {
            alert('Informe um valor válido.');
            valido = false;
        } else if (isNaN(totalParcelas) || totalParcelas < 1) {
            alert('Informe uma quantidade de parcelas válida.');
            valido = false;
        }

        return valido;
    };

    /**
     * Cria uma nova recorrência via API (o backend já gera todas as parcelas) e atualiza
     * recorrências, cartões (o gasto do mês pode mudar) e transações.
     *
     * @param {string} description descrição
     * @param {number} value valor por parcela
     * @param {number} contaId id da conta
     * @param {number} cardId id do cartão, ou null
     * @param {number} totalParcelas quantidade de parcelas
     * @param {string} dataInicio data de início no formato YYYY-MM-DD, ou string vazia (omite, backend usa hoje)
     * @returns
     */
    self.criarRecorrencia = function (description, value, contaId, cardId, totalParcelas, dataInicio) {
        var corpo = {
            descricao: description,
            valor: value,
            tipo: self.state.currentTypeRecorrencia === 'in' ? 'ENTRADA' : 'SAIDA',
            categoria: self.state.currentCategoriaRecorrencia,
            contaId: contaId,
            cartaoId: cardId,
            totalParcelas: totalParcelas
        };

        if (dataInicio) {
            corpo.dataInicio = dataInicio;
        }

        $.ajax({
            url: self.apiBaseUrl + '/api/recorrencias',
            method: 'POST',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify(corpo),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function () {
                self.closeModal('#modalRecorrencia');
                self.carregarRecorrencias();
                self.carregarCartoes();
                self.carregarTransacoes();
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };

    self.abrirModalEditarValorRecorrencia = function (recorrencia) {
        self.state.editingValorRecorrenciaId = recorrencia.id;
        $('#inputRecorrenciaNewValue').val(recorrencia.valor);
        self.openModal('#modalEditRecorrenciaValue');
    };

    self.atualizarValorRecorrencia = function () {
        var novoValor = parseFloat($('#inputRecorrenciaNewValue').val().replace(',', '.'));

        if (isNaN(novoValor) || novoValor <= 0) {
            alert('Informe um valor válido.');
            return;
        }

        $.ajax({
            url: self.apiBaseUrl + '/api/recorrencias/' + self.state.editingValorRecorrenciaId + '/valor',
            method: 'PUT',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify({ valor: novoValor }),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function () {
                self.closeModal('#modalEditRecorrenciaValue');
                self.carregarRecorrencias();
                self.carregarTransacoes();
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
     * Cancela as parcelas futuras de uma recorrência, após confirmação do usuário.
     *
     * @param {number} id id da recorrência
     * @returns
     */
    self.cancelarRecorrenciaFuturas = function (id) {
        if (confirm('Cancelar as parcelas futuras dessa recorrência? As parcelas passadas continuam no histórico.')) {
            $.ajax({
                url: self.apiBaseUrl + '/api/recorrencias/' + id,
                method: 'DELETE',
                headers: self.cabecalhoAuth(),
                beforeSend: function () {
                    self.mostrarCarregando();
                },
                success: function () {
                    self.carregarRecorrencias();
                    self.carregarTransacoes();
                },
                error: function (jqXHR) {
                    self.tratarErroRequisicao(jqXHR);
                },
                complete: function () {
                    self.esconderCarregando();
                }
            });
        }
    };
```

- [ ] **Step 4: Ligar os eventos e chamar `carregarRecorrencias` na inicialização**

Logo depois do bloco de eventos de `#btnConfirmDeleteConta` (antes de `self.exibirDadosUsuario();` no final de `iniciar()`), adicionar:

```js
            $('#btnNewRecorrencia').on('click', function () {
                self.openModal('#modalRecorrencia');
                self.resetRecorrenciaModal();
            });

            $('#modalRecorrencia').on('click', function (e) {
                if ($(e.target).is('#modalRecorrencia')) {
                    self.closeModal('#modalRecorrencia');
                }
            });

            $('#btnRecorrenciaTypeIn, #btnRecorrenciaTypeOut').on('click', function () {
                self.state.currentTypeRecorrencia = $(this).data('type');
                self.applyRecorrenciaTypeStyle(self.state.currentTypeRecorrencia);
            });

            $('#btnConfirmRecorrencia').on('click', function () {
                var description = $.trim($('#inputRecorrenciaDescription').val());
                var rawValue = $('#inputRecorrenciaValue').val().replace(',', '.');
                var value = parseFloat(rawValue);
                var contaId = parseInt($('#inputRecorrenciaAccount').val());
                var cardId = parseInt($('#inputRecorrenciaCard').val()) || null;
                var totalParcelas = parseInt($('#inputRecorrenciaParcelas').val());
                var dataInicio = $('#inputRecorrenciaDataInicio').val();

                if (!contaId) {
                    alert('Crie uma conta antes de lançar uma recorrência.');
                } else if (self.validateRecorrencia(description, value, totalParcelas)) {
                    if (self.state.currentCategoriaRecorrencia) {
                        self.criarRecorrencia(description, value, contaId, cardId, totalParcelas, dataInicio);
                    } else {
                        alert('Escolha uma categoria.');
                    }
                }
            });

            $('#modalEditRecorrenciaValue').on('click', function (e) {
                if ($(e.target).is('#modalEditRecorrenciaValue')) {
                    self.closeModal('#modalEditRecorrenciaValue');
                }
            });

            $('#btnConfirmEditRecorrenciaValue').on('click', self.atualizarValorRecorrencia);
```

Trocar a linha final:

```js
            self.exibirDadosUsuario();
            self.exibirSaudacao();
            self.renderizarPeriodo();
            self.buildFilterRow();
            self.buildColorPicker();
            self.updateAccountSelector('all');
            self.carregarContas();
            self.carregarCartoes();
            self.carregarTransacoes();
```

por:

```js
            self.exibirDadosUsuario();
            self.exibirSaudacao();
            self.renderizarPeriodo();
            self.buildFilterRow();
            self.buildColorPicker();
            self.updateAccountSelector('all');
            self.carregarContas();
            self.carregarCartoes();
            self.carregarTransacoes();
            self.carregarRecorrencias();
```

(Confira a ordem exata da sequência final antes de trocar — ela reflete o estado atual do arquivo depois da feature de Contas; se estiver ligeiramente diferente da mostrada aqui, mantenha a ordem existente e só acrescente `self.carregarRecorrencias();` como última linha.)

- [ ] **Step 5: Verificação**

```bash
node --check assets/js/script.js
```

Esperado: sem erros. Depois, grep de sanidade:

```bash
grep -c "self.carregarRecorrencias\b" assets/js/script.js
```

Esperado: pelo menos 2 (a definição da função + a chamada em `iniciar()`).

- [ ] **Step 6: Pare aqui**

Não commite ainda — a verificação end-to-end é a próxima (e última) tarefa deste plano.

---

### Task 6: Verificação manual end-to-end (backend + frontend juntos)

**Files:** nenhum (só verificação)

- [ ] **Step 1: Subir backend e frontend**

Ver Pré-requisitos no topo deste plano (porta **5501** no front, pra bater com o CORS default do backend).

- [ ] **Step 2: Fluxo completo no navegador**

1. Faça login com um usuário existente (ou cadastre um novo).
2. Vá em "Recorrências" na sidebar — a lista deve estar vazia (ou mostrar recorrências criadas via curl na Task 3, se você usou o mesmo banco).
3. Clique em "Nova recorrência". Preencha: descrição "Aluguel", valor 1200, Saída, categoria Moradia, conta (a que já existir), 3 parcelas, sem cartão, data de início em branco (usa hoje). Confirme.
4. Confirme que a recorrência aparece na lista com "0 de 3" (nenhuma parcela passou ainda, já que a primeira é hoje) ou "1 de 3" dependendo de como você interpretar "hoje" como decorrida — o importante é que `parcelasRestantes` bata com 3 logo após criar.
5. Vá no Dashboard — confirme que a transação da parcela de hoje aparece na lista de movimentações, e que o saldo/resumo refletem o valor lançado.
6. Volte em "Recorrências", clique no ícone de editar (lápis) na recorrência criada, mude o valor pra 1350, confirme. Volte no Dashboard e confirme que a transação de hoje (parte das "futuras") mudou de valor.
7. Volte em "Recorrências", clique em cancelar (lixeira). Confirme o alerta de confirmação. Confirme que a recorrência passa a mostrar "X de 0" ou desaparece a contagem de futuras, e que a(s) transação(ões) futura(s) some(m) da lista de movimentações do Dashboard — a de hoje, se já "passada" no momento do teste, pode continuar.

- [ ] **Step 3: Pare aqui — fim do plano**

Não commite. Se tudo no Step 2 se comportou como descrito, o plano está implementado. Revise o `git diff` completo dos dois repositórios e faça os commits você mesmo.
