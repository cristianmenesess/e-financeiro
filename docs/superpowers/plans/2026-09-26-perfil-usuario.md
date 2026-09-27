# Perfil do Usuário — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clicar no usuário (sidebar/topbar) abre o modal "Meu perfil", onde ele troca nome, e-mail, senha e foto (Cloudinary) e pode excluir o próprio cadastro com todos os dados.

**Architecture:** Backend ganha `ControladorPerfil` (`/api/perfil`) → `ServicoPerfil` (regras) → `ServicoFotoPerfil` (único ponto que fala com o Cloudinary). Exclusão do cadastro apaga tudo em lote numa transação e só apaga a foto no Cloudinary depois do commit. Front ganha o `#modalProfile` no `index.html` e os métodos correspondentes na `TelaDashboard` (`script.js`), seguindo o padrão "Tela".

**Tech Stack:** Java 17 + Spring Boot 4.1, Spring Data JPA, Flyway, Cloudinary Java SDK (`cloudinary-http5` 2.3.0), JUnit 5 + MockMvc + Testcontainers; front em HTML + jQuery sem build (ESLint/Stylelint via `npm run check`).

**Spec:** `docs/superpowers/specs/2026-09-26-perfil-usuario-design.md`

**Regras do projeto que valem pra todas as tasks:**
- Nunca criar commit — os passos "Checkpoint" só rodam a verificação; o commit é feito manualmente pelo Cristian.
- Código, nomes e mensagens em português; JavaDoc em todo método público; DTOs sempre `record`; controllers com `Authentication autenticacao` + `autenticacao.getName()`.
- Rodar testes do backend exige o Docker rodando (Testcontainers).
- Refinamento em relação ao spec: a validação do tipo da foto confere os **bytes iniciais do arquivo** (assinatura JPEG/PNG/WEBP) em vez do Content-Type, que o cliente pode forjar.

---

## Mapa de arquivos

**Backend (`D:\PROJETOS\e-financeiro`)**

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `build.gradle` | Modificar | Dependência do SDK do Cloudinary |
| `src/main/resources/application.properties` | Modificar | `app.cloudinary.url`, limites de multipart |
| `src/main/resources/db/migration/V7__foto_perfil.sql` | Criar | Coluna `usuarios.foto_url` |
| `src/main/java/com/efinanceiro/dominio/Usuario.java` | Modificar | Campo `fotoUrl` |
| `src/main/java/com/efinanceiro/dto/resposta/RespostaAutenticacao.java` | Modificar | Campo `fotoUrl` |
| `src/main/java/com/efinanceiro/dto/resposta/RespostaPerfil.java` | Criar | Resposta do perfil |
| `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoAtualizacaoPerfil.java` | Criar | Nome/e-mail/senha atual |
| `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoTrocaSenha.java` | Criar | Senha atual + nova |
| `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoExclusaoCadastro.java` | Criar | Senha pra excluir |
| `src/main/java/com/efinanceiro/servico/NormalizadorEmail.java` | Criar | `trim().toLowerCase()` compartilhado |
| `src/main/java/com/efinanceiro/servico/ServicoAutenticacao.java` | Modificar | Usar `NormalizadorEmail`, devolver `fotoUrl` |
| `src/main/java/com/efinanceiro/excecao/DadosInvalidosException.java` | Criar | 400 de regra do serviço |
| `src/main/java/com/efinanceiro/excecao/ServicoExternoIndisponivelException.java` | Criar | 502 (Cloudinary fora) |
| `src/main/java/com/efinanceiro/excecao/TratadorGlobalDeExcecoes.java` | Modificar | Handlers 400/502/upload |
| `src/main/java/com/efinanceiro/configuracao/ConfiguracaoCloudinary.java` | Criar | Bean `Cloudinary` |
| `src/main/java/com/efinanceiro/servico/ServicoFotoPerfil.java` | Criar | Enviar/apagar foto no Cloudinary |
| `src/main/java/com/efinanceiro/servico/ServicoLimiteRequisicoes.java` | Modificar | Regra `SENHA_ATUAL_POR_USUARIO` |
| `src/main/java/com/efinanceiro/repositorio/Repositorio{Transacao,Recorrencia,Cartao,Conta,Usuario}.java` | Modificar | Exclusões em lote por usuário |
| `src/main/java/com/efinanceiro/servico/ServicoPerfil.java` | Criar | Regras do perfil |
| `src/main/java/com/efinanceiro/controlador/ControladorPerfil.java` | Criar | Endpoints `/api/perfil` |
| `src/test/java/com/efinanceiro/suporte/TesteIntegracao.java` | Modificar | Mock do `ServicoFotoPerfil`, helper de login |
| `src/test/java/com/efinanceiro/controlador/PerfilTeste.java` | Criar | Testes do perfil |
| `README.md` | Modificar | Variável `CLOUDINARY_URL` e tabela "Perfil" |

**Front (`D:\PROJETOS\e-financeiro-front`)**

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `assets/js/login.js`, `assets/js/cadastro.js` | Modificar | Guardar `fotoUrl` na sessão |
| `index.html` | Modificar | Bloco do usuário vira botão; `#modalProfile` |
| `assets/css/style.css` | Modificar | Estilos do modal de perfil |
| `assets/js/script.js` | Modificar | Sessão, avatar com foto, lógica do modal |
| `sw.js` | Modificar | Cache `v5` (senão o PWA serve o JS antigo) |

---

### Task 1: Base do backend — coluna da foto, `fotoUrl` no login e normalização de e-mail compartilhada

**Files:**
- Create: `src/main/resources/db/migration/V7__foto_perfil.sql`
- Create: `src/main/java/com/efinanceiro/servico/NormalizadorEmail.java`
- Modify: `src/main/java/com/efinanceiro/dominio/Usuario.java`
- Modify: `src/main/java/com/efinanceiro/dto/resposta/RespostaAutenticacao.java`
- Modify: `src/main/java/com/efinanceiro/servico/ServicoAutenticacao.java`
- Test: `src/test/java/com/efinanceiro/controlador/AutenticacaoTeste.java`

- [ ] **Step 1: Escrever o teste que falha** — adicionar no fim de `AutenticacaoTeste` (antes do `}` final):

```java
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
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "com.efinanceiro.controlador.AutenticacaoTeste" --no-daemon`
Expected: FAIL em `loginDevolveFotoUrlNulaParaQuemNaoTemFoto` — `No value at JSON path "$.fotoUrl"`.

- [ ] **Step 3: Criar a migration** `V7__foto_perfil.sql`:

```sql
-- URL da foto de perfil no Cloudinary (secure_url, já com a versão). Nulo = sem foto.
ALTER TABLE usuarios ADD COLUMN foto_url VARCHAR(500);
```

- [ ] **Step 4: Campo na entidade** — em `Usuario.java`, logo depois do campo `senhaAlteradaEm`:

```java
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;
```

- [ ] **Step 5: `fotoUrl` na resposta de autenticação** — substituir o conteúdo de `RespostaAutenticacao.java`:

```java
package com.efinanceiro.dto.resposta;

public record RespostaAutenticacao(
        String token,
        String nome,
        String email,
        String fotoUrl
) {
}
```

- [ ] **Step 6: Criar `NormalizadorEmail.java`**:

```java
package com.efinanceiro.servico;

import java.util.Locale;

/**
 * Regra única de normalização de e-mail (cadastro, login, esqueci a senha, troca de e-mail no
 * perfil): sem espaços nas pontas e minúsculo.
 */
public final class NormalizadorEmail {

    private NormalizadorEmail() {
    }

    /**
     * Normaliza um e-mail digitado pelo usuário.
     *
     * @param email E-mail como veio na requisição
     * @return E-mail sem espaços nas pontas e em minúsculas
     */
    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 7: Usar no `ServicoAutenticacao`** — apagar o método privado `normalizarEmail` e o `import java.util.Locale;`; trocar as três chamadas `normalizarEmail(requisicao.email())` por `NormalizadorEmail.normalizar(requisicao.email())`; e trocar as duas linhas que montam a resposta:

```java
        return new RespostaAutenticacao(token, usuario.getNome(), usuario.getEmail(), usuario.getFotoUrl());
```

(em `cadastrar` e em `login`).

- [ ] **Step 8: Rodar a suíte toda**

Run: `./gradlew test --no-daemon`
Expected: PASS (42 testes — os 41 anteriores + o novo; a V7 aplicada no Testcontainers).

- [ ] **Step 9: Checkpoint** — `git status` pra conferir os arquivos; commit fica com o Cristian.

---

### Task 2: Exceções novas e handlers (400 de serviço, 502, upload)

**Files:**
- Create: `src/main/java/com/efinanceiro/excecao/DadosInvalidosException.java`
- Create: `src/main/java/com/efinanceiro/excecao/ServicoExternoIndisponivelException.java`
- Modify: `src/main/java/com/efinanceiro/excecao/TratadorGlobalDeExcecoes.java`
- Modify: `src/main/resources/application.properties`

(Os testes destes handlers vêm na Task 6, junto com os endpoints que os disparam.)

- [ ] **Step 1: Criar `DadosInvalidosException.java`**:

```java
package com.efinanceiro.excecao;

public class DadosInvalidosException extends RuntimeException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
```

- [ ] **Step 2: Criar `ServicoExternoIndisponivelException.java`**:

```java
package com.efinanceiro.excecao;

public class ServicoExternoIndisponivelException extends RuntimeException {

    public ServicoExternoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
```

- [ ] **Step 3: Handlers no `TratadorGlobalDeExcecoes`** — adicionar os imports:

```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
```

e os métodos abaixo, logo antes do handler de `TokenInvalidoOuExpiradoException`:

```java
    /**
     * Trata dado recusado por uma regra do serviço (ex: trocar o e-mail sem informar a senha,
     * foto em formato não aceito).
     *
     * @param excecao Exceção de dados inválidos
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<Map<String, Object>> tratarDadosInvalidos(DadosInvalidosException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata falha de um serviço externo (ex: Cloudinary fora do ar). O detalhe fica no log de quem
     * lançou a exceção; aqui só volta uma mensagem amigável.
     *
     * @param excecao Exceção de serviço externo indisponível
     * @return Corpo de erro com status 502
     */
    @ExceptionHandler(ServicoExternoIndisponivelException.class)
    public ResponseEntity<Map<String, Object>> tratarServicoExternoIndisponivel(ServicoExternoIndisponivelException excecao) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(corpoDeErro(excecao.getMessage()));
    }

    /**
     * Trata upload acima do limite configurado em spring.servlet.multipart (barrado antes de chegar
     * no controller).
     *
     * @param excecao Exceção de tamanho de upload excedido
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tratarUploadGrandeDemais(MaxUploadSizeExceededException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("A foto deve ter no máximo 5 MB"));
    }

    /**
     * Trata requisição multipart sem o arquivo esperado.
     *
     * @param excecao Exceção de parte do multipart ausente
     * @return Corpo de erro com status 400
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> tratarArquivoAusente(MissingServletRequestPartException excecao) {
        return ResponseEntity.badRequest().body(corpoDeErro("Envie o arquivo no campo '" + excecao.getRequestPartName() + "'"));
    }
```

- [ ] **Step 4: Limites de upload** — em `application.properties`, depois da linha `server.forward-headers-strategy=native`:

```properties

# Foto de perfil: o front já recorta pra 512x512 (~50 KB); o limite só barra envio direto abusivo
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava --no-daemon -q`
Expected: sem erros e sem avisos de lint.

- [ ] **Step 6: Checkpoint** — commit fica com o Cristian.

---

### Task 3: Integração com o Cloudinary (`ServicoFotoPerfil`)

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/efinanceiro/configuracao/ConfiguracaoCloudinary.java`
- Create: `src/main/java/com/efinanceiro/servico/ServicoFotoPerfil.java`

(Classe de integração pura com serviço externo: nos testes ela é sempre mockada, como o `ServicoEmail`. A validação do arquivo mora no `ServicoPerfil` e é testada na Task 6.)

- [ ] **Step 1: Dependência** — em `build.gradle`, logo depois da linha do `bucket4j`:

```groovy
	implementation 'com.cloudinary:cloudinary-http5:2.3.0'
```

- [ ] **Step 2: Propriedade** — em `application.properties`, depois de `app.frontend.url=...`:

```properties

app.cloudinary.url=${CLOUDINARY_URL:}
```

- [ ] **Step 3: Criar `ConfiguracaoCloudinary.java`**:

```java
package com.efinanceiro.configuracao;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ConfiguracaoCloudinary {

    /**
     * Cliente do Cloudinary configurado pela URL que o próprio painel do Cloudinary fornece
     * (cloudinary://chave:segredo@nome-da-nuvem). Sem a variável, o cliente sobe vazio: a aplicação
     * funciona normalmente e só o envio de foto falha (502), em vez de derrubar a inicialização.
     *
     * @param url Valor de CLOUDINARY_URL
     * @return Cliente do Cloudinary
     */
    @Bean
    public Cloudinary cloudinary(@Value("${app.cloudinary.url}") String url) {
        return url.isBlank() ? new Cloudinary(Map.of()) : new Cloudinary(url);
    }
}
```

- [ ] **Step 4: Criar `ServicoFotoPerfil.java`**:

```java
package com.efinanceiro.servico;

import com.cloudinary.Cloudinary;
import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Única classe que conhece o Cloudinary. Cada usuário tem um identificador fixo de foto
 * (efinanceiro/usuarios/{id}): enviar uma foto nova sobrescreve a anterior, então nunca sobra
 * foto órfã por troca.
 */
@Slf4j
@Service
public class ServicoFotoPerfil {

    private final Cloudinary cloudinary;

    public ServicoFotoPerfil(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Envia a foto de perfil do usuário, já recortada no servidor do Cloudinary em 512x512
     * centralizada no rosto.
     *
     * @param usuarioId Id do usuário dono da foto
     * @param conteudo Bytes da imagem (JPG, PNG ou WEBP, já validados)
     * @return URL segura da foto (com a versão, o que evita cache da foto antiga)
     * @throws ServicoExternoIndisponivelException se o Cloudinary recusar ou não responder
     */
    public String enviar(Long usuarioId, byte[] conteudo) {
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(conteudo, Map.of(
                    "public_id", idPublico(usuarioId),
                    "overwrite", true,
                    "invalidate", true,
                    "resource_type", "image",
                    "allowed_formats", "jpg,png,webp",
                    "transformation", "c_fill,g_face,w_512,h_512"
            ));

            return (String) resultado.get("secure_url");
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao enviar a foto de perfil do usuário {} ao Cloudinary", usuarioId, e);
            throw new ServicoExternoIndisponivelException("Não foi possível salvar a foto agora. Tente de novo.");
        }
    }

    /**
     * Apaga a foto de perfil do usuário no Cloudinary.
     *
     * @param usuarioId Id do usuário dono da foto
     * @throws ServicoExternoIndisponivelException se o Cloudinary não responder
     */
    public void apagar(Long usuarioId) {
        try {
            cloudinary.uploader().destroy(idPublico(usuarioId), Map.of("invalidate", true));
        } catch (IOException | RuntimeException e) {
            log.error("Falha ao apagar a foto de perfil do usuário {} no Cloudinary", usuarioId, e);
            throw new ServicoExternoIndisponivelException("Não foi possível remover a foto agora. Tente de novo.");
        }
    }

    private String idPublico(Long usuarioId) {
        return "efinanceiro/usuarios/" + usuarioId;
    }
}
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava --no-daemon -q`
Expected: sem erros e sem avisos de lint (se o SDK gerar aviso `unchecked` no `upload`/`destroy`, trocar o `Map.of(...)` por uma variável `Map<String, Object> opcoes = Map.of(...)` antes da chamada).

- [ ] **Step 6: Checkpoint** — commit fica com o Cristian.

---

### Task 4: Limite de tentativas da senha atual e exclusões em lote por usuário

**Files:**
- Modify: `src/main/java/com/efinanceiro/servico/ServicoLimiteRequisicoes.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioTransacao.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioRecorrencia.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioCartao.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioConta.java`
- Modify: `src/main/java/com/efinanceiro/repositorio/RepositorioUsuario.java`

(Testados de ponta a ponta pela Task 6 — exclusão do cadastro e 429.)

- [ ] **Step 1: Regra nova** — no enum `Regra` do `ServicoLimiteRequisicoes`, trocar `REDEFINIR_SENHA_POR_IP(20, Duration.ofMinutes(15));` por:

```java
        REDEFINIR_SENHA_POR_IP(20, Duration.ofMinutes(15)),
        SENHA_ATUAL_POR_USUARIO(5, Duration.ofMinutes(15));
```

- [ ] **Step 2: `RepositorioTransacao`** — adicionar antes do último `}`:

```java
    /**
     * Apaga todas as transações de um usuário, num único DELETE — usado na exclusão do cadastro.
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Transacao t where t.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
```

- [ ] **Step 3: `RepositorioRecorrencia`** — adicionar antes do último `}`:

```java
    /**
     * Apaga todas as recorrências de um usuário, num único DELETE — usado na exclusão do cadastro
     * (depois das transações, que apontam pra elas).
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Recorrencia r where r.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
```

- [ ] **Step 4: `RepositorioCartao`** — adicionar os imports `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param` e, antes do último `}`:

```java
    /**
     * Apaga todos os cartões de um usuário, num único DELETE — usado na exclusão do cadastro.
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Cartao c where c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
```

- [ ] **Step 5: `RepositorioConta`** — mesmos três imports da Step 4 e, antes do último `}`:

```java
    /**
     * Apaga todas as contas de um usuário, num único DELETE — usado na exclusão do cadastro.
     *
     * @param usuarioId Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Conta c where c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
```

- [ ] **Step 6: `RepositorioUsuario`** — mesmos três imports da Step 4 e, antes do último `}`:

```java
    /**
     * Apaga o usuário num DELETE direto — usado por último na exclusão do cadastro, depois de
     * todos os dados que apontam pra ele.
     *
     * @param id Id do usuário
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Usuario u where u.id = :id")
    void apagarPorId(@Param("id") Long id);
```

- [ ] **Step 7: Compilar**

Run: `./gradlew compileJava --no-daemon -q`
Expected: sem erros.

- [ ] **Step 8: Checkpoint** — commit fica com o Cristian.

---

### Task 5: DTOs do perfil

**Files:**
- Create: `src/main/java/com/efinanceiro/dto/resposta/RespostaPerfil.java`
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoAtualizacaoPerfil.java`
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoTrocaSenha.java`
- Create: `src/main/java/com/efinanceiro/dto/requisicao/RequisicaoExclusaoCadastro.java`

- [ ] **Step 1: `RespostaPerfil.java`**:

```java
package com.efinanceiro.dto.resposta;

/**
 * Dados do perfil do usuário logado. O token só vem preenchido quando um token novo foi emitido
 * (troca de e-mail ou de senha) — o front deve trocar o da sessão por ele.
 */
public record RespostaPerfil(
        String nome,
        String email,
        String fotoUrl,
        String token
) {
}
```

- [ ] **Step 2: `RequisicaoAtualizacaoPerfil.java`**:

```java
package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoAtualizacaoPerfil(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email,

        // Obrigatória só quando o e-mail muda — conferido no serviço
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senhaAtual
)
{}
```

- [ ] **Step 3: `RequisicaoTrocaSenha.java`**:

```java
package com.efinanceiro.dto.requisicao;

import com.efinanceiro.dto.validacao.TamanhoMaximoEmBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoTrocaSenha(

        @NotBlank(message = "A senha atual é obrigatória")
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senhaAtual,

        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 8, message = "A nova senha deve ter no mínimo 8 caracteres")
        @TamanhoMaximoEmBytes(value = 72, message = "A nova senha deve ter no máximo 72 caracteres")
        String novaSenha
)
{}
```

- [ ] **Step 4: `RequisicaoExclusaoCadastro.java`**:

```java
package com.efinanceiro.dto.requisicao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoExclusaoCadastro(

        @NotBlank(message = "A senha é obrigatória")
        @Size(max = 200, message = "A senha deve ter no máximo 200 caracteres")
        String senha
)
{}
```

- [ ] **Step 5: Compilar** — `./gradlew compileJava --no-daemon -q`, sem erros.

- [ ] **Step 6: Checkpoint** — commit fica com o Cristian.

---

### Task 6: `ServicoPerfil` + `ControladorPerfil` (TDD, por endpoint)

**Files:**
- Modify: `src/test/java/com/efinanceiro/suporte/TesteIntegracao.java`
- Create: `src/test/java/com/efinanceiro/controlador/PerfilTeste.java`
- Create: `src/main/java/com/efinanceiro/servico/ServicoPerfil.java`
- Create: `src/main/java/com/efinanceiro/controlador/ControladorPerfil.java`

- [ ] **Step 1: Base de testes** — em `TesteIntegracao.java`, adicionar o import `com.efinanceiro.servico.ServicoFotoPerfil;`, o mock ao lado do `servicoEmail`:

```java
    @MockitoBean
    protected ServicoFotoPerfil servicoFotoPerfil;
```

e o helper de login logo depois de `cadastrarUsuario()` sem argumentos:

```java
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
```

- [ ] **Step 2: Escrever todos os testes do perfil (falhando)** — criar `PerfilTeste.java`:

```java
package com.efinanceiro.controlador;

import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import com.efinanceiro.suporte.TesteIntegracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        mockMvc.perform(comToken(delete("/api/perfil"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\": \"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Senha incorreta"));

        login(email, SENHA_PADRAO);
    }

    @Test
    void excluirCadastroApagaTodosOsDadosDoUsuarioEPreservaOsDosOutros() throws Exception {
        String email = emailUnico();
        String token = cadastrarUsuario(email);
        Long contaId = idContaPadrao(token);
        Long usuarioId = jdbcTemplate.queryForObject("select id from usuarios where email = ?", Long.class, email);

        String tokenVizinho = cadastrarUsuario();
        criarTransacao(tokenVizinho, idContaPadrao(tokenVizinho), "ENTRADA", "10.00", "2026-03-01", null);

        String cartao = mockMvc.perform(comToken(post("/api/cartoes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Cartão\", \"corFundo\": \"#111111\", \"corTexto\": \"#FFFFFF\"}"))
                .andReturn().getResponse().getContentAsString();
        Long cartaoId = ((Number) JsonPath.read(cartao, "$.id")).longValue();
        criarTransacao(token, contaId, "SAIDA", "50.00", "2026-03-01", cartaoId);
        mockMvc.perform(comToken(post("/api/recorrencias"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Aluguel", "valor": 100, "tipo": "SAIDA", "categoria": "MORADIA",
                                 "contaId": %d, "cartaoId": %d, "totalParcelas": 3}
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

        for (String tabela : new String[]{"transacoes", "recorrencias", "cartoes", "contas"}) {
            Integer restantes = jdbcTemplate.queryForObject(
                    "select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuarioId);
            assertThat(restantes).as(tabela).isZero();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from usuarios where id = ?", Integer.class, usuarioId)).isZero();

        verify(servicoFotoPerfil).apagar(usuarioId);

        mockMvc.perform(comToken(get("/api/perfil"), token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(comToken(get("/api/transacoes"), tokenVizinho))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests "com.efinanceiro.controlador.PerfilTeste" --no-daemon`
Expected: FAIL em todos — `Status expected:<200> but was:<404>` ("Rota não encontrada"), porque `/api/perfil` ainda não existe.

- [ ] **Step 4: Criar `ServicoPerfil.java`**:

```java
package com.efinanceiro.servico;

import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoPerfil;
import com.efinanceiro.dto.requisicao.RequisicaoTrocaSenha;
import com.efinanceiro.dto.resposta.RespostaPerfil;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.EmailJaCadastradoException;
import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import com.efinanceiro.seguranca.ServicoJwt;
import com.efinanceiro.servico.ServicoLimiteRequisicoes.Regra;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@Transactional
public class ServicoPerfil {

    private static final long TAMANHO_MAXIMO_FOTO = 5L * 1024 * 1024;

    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final RepositorioCartao repositorioCartao;
    private final RepositorioConta repositorioConta;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ServicoJwt servicoJwt;
    private final ServicoLimiteRequisicoes servicoLimiteRequisicoes;
    private final ServicoFotoPerfil servicoFotoPerfil;
    private final Clock relogio;

    public ServicoPerfil(RepositorioUsuario repositorioUsuario,
                          RepositorioTransacao repositorioTransacao,
                          RepositorioRecorrencia repositorioRecorrencia,
                          RepositorioCartao repositorioCartao,
                          RepositorioConta repositorioConta,
                          BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                          PasswordEncoder codificadorDeSenha,
                          ServicoJwt servicoJwt,
                          ServicoLimiteRequisicoes servicoLimiteRequisicoes,
                          ServicoFotoPerfil servicoFotoPerfil,
                          Clock relogio) {
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.repositorioCartao = repositorioCartao;
        this.repositorioConta = repositorioConta;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.servicoJwt = servicoJwt;
        this.servicoLimiteRequisicoes = servicoLimiteRequisicoes;
        this.servicoFotoPerfil = servicoFotoPerfil;
        this.relogio = relogio;
    }

    /**
     * Busca os dados do perfil do usuário logado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Nome, e-mail e foto (sem token)
     */
    @Transactional(readOnly = true)
    public RespostaPerfil buscarPerfil(String emailUsuario) {
        return paraResposta(buscadorRecursosDoUsuario.buscarUsuario(emailUsuario), null);
    }

    /**
     * Atualiza nome e e-mail. Trocar o e-mail exige a senha atual, não pode usar o e-mail de outro
     * usuário e gera um token novo (o e-mail vai dentro do JWT; os tokens antigos deixam de valer).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Novo nome, novo e-mail e (se o e-mail mudar) a senha atual
     * @return Perfil atualizado, com token novo só se o e-mail mudou
     */
    public RespostaPerfil atualizarPerfil(String emailUsuario, RequisicaoAtualizacaoPerfil requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        String novoEmail = NormalizadorEmail.normalizar(requisicao.email());
        String tokenNovo = null;

        if (!novoEmail.equals(usuario.getEmail())) {
            if (requisicao.senhaAtual() == null || requisicao.senhaAtual().isBlank()) {
                throw new DadosInvalidosException("Informe a senha atual para trocar o e-mail");
            }

            conferirSenhaAtual(usuario, requisicao.senhaAtual());

            boolean emUsoPorOutro = repositorioUsuario.findFirstByEmailIgnoreCaseOrderByIdAsc(novoEmail)
                    .filter(outro -> !outro.getId().equals(usuario.getId()))
                    .isPresent();

            if (emUsoPorOutro) {
                throw new EmailJaCadastradoException("Já existe um usuário cadastrado com esse e-mail");
            }

            usuario.setEmail(novoEmail);
            tokenNovo = servicoJwt.gerarToken(novoEmail);
        }

        usuario.setNome(requisicao.nome().trim());
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, tokenNovo);
    }

    /**
     * Troca a senha do usuário logado. Todos os tokens emitidos antes deixam de valer (outros
     * aparelhos são deslogados) e a sessão atual recebe um token novo.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Senha atual e nova senha
     * @return Perfil com o token novo
     */
    public RespostaPerfil trocarSenha(String emailUsuario, RequisicaoTrocaSenha requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        conferirSenhaAtual(usuario, requisicao.senhaAtual());

        usuario.setSenhaHash(codificadorDeSenha.encode(requisicao.novaSenha()));
        usuario.setSenhaAlteradaEm(Instant.now(relogio));
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, servicoJwt.gerarToken(usuario.getEmail()));
    }

    /**
     * Troca a foto de perfil. O tipo é conferido pelos bytes iniciais do arquivo (não pelo
     * Content-Type, que o cliente pode forjar). Se o Cloudinary falhar, a foto anterior continua.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param arquivo Arquivo enviado no campo "foto"
     * @return Perfil com a URL da foto nova
     */
    public RespostaPerfil atualizarFoto(String emailUsuario, MultipartFile arquivo) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        byte[] conteudo = validarFoto(arquivo);

        usuario.setFotoUrl(servicoFotoPerfil.enviar(usuario.getId(), conteudo));
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, null);
    }

    /**
     * Remove a foto de perfil (apaga no Cloudinary e volta a exibir as iniciais).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Perfil sem foto
     */
    public RespostaPerfil removerFoto(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        if (usuario.getFotoUrl() != null) {
            servicoFotoPerfil.apagar(usuario.getId());
            usuario.setFotoUrl(null);
            repositorioUsuario.save(usuario);
        }

        return paraResposta(usuario, null);
    }

    /**
     * Exclui o cadastro do usuário e todos os dados dele (transações, recorrências, cartões,
     * contas e foto), mediante a senha atual. A foto só é apagada no Cloudinary depois que o banco
     * confirmou a exclusão: se o banco falhar, a foto não se perde à toa.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param senha Senha atual, pra confirmar a exclusão
     */
    public void excluirCadastro(String emailUsuario, String senha) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        conferirSenhaAtual(usuario, senha);

        Long usuarioId = usuario.getId();
        boolean tinhaFoto = usuario.getFotoUrl() != null;

        // Ordem importa por causa das FKs: quem aponta pra alguém é apagado antes
        repositorioTransacao.deleteByUsuarioId(usuarioId);
        repositorioRecorrencia.deleteByUsuarioId(usuarioId);
        repositorioCartao.deleteByUsuarioId(usuarioId);
        repositorioConta.deleteByUsuarioId(usuarioId);
        repositorioUsuario.apagarPorId(usuarioId);

        if (tinhaFoto) {
            apagarFotoDepoisDoCommit(usuarioId);
        }
    }

    private void conferirSenhaAtual(Usuario usuario, String senha) {
        servicoLimiteRequisicoes.consumir(Regra.SENHA_ATUAL_POR_USUARIO, usuario.getId().toString());

        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Senha incorreta");
        }
    }

    private byte[] validarFoto(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new DadosInvalidosException("Selecione uma foto");
        }

        if (arquivo.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new DadosInvalidosException("A foto deve ter no máximo 5 MB");
        }

        byte[] conteudo;

        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new DadosInvalidosException("Não foi possível ler a foto enviada");
        }

        if (!ehImagemAceita(conteudo)) {
            throw new DadosInvalidosException("A foto deve ser JPG, PNG ou WEBP");
        }

        return conteudo;
    }

    // Assinatura dos primeiros bytes de cada formato aceito
    private boolean ehImagemAceita(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';

        return jpeg || png || webp;
    }

    private void apagarFotoDepoisDoCommit(Long usuarioId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    servicoFotoPerfil.apagar(usuarioId);
                } catch (ServicoExternoIndisponivelException e) {
                    log.error("Cadastro {} excluído, mas a foto ficou no Cloudinary", usuarioId);
                }
            }
        });
    }

    private RespostaPerfil paraResposta(Usuario usuario, String token) {
        return new RespostaPerfil(usuario.getNome(), usuario.getEmail(), usuario.getFotoUrl(), token);
    }
}
```

- [ ] **Step 5: Criar `ControladorPerfil.java`**:

```java
package com.efinanceiro.controlador;

import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoPerfil;
import com.efinanceiro.dto.requisicao.RequisicaoExclusaoCadastro;
import com.efinanceiro.dto.requisicao.RequisicaoTrocaSenha;
import com.efinanceiro.dto.resposta.RespostaPerfil;
import com.efinanceiro.servico.ServicoPerfil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/perfil")
public class ControladorPerfil {

    private final ServicoPerfil servicoPerfil;

    public ControladorPerfil(ServicoPerfil servicoPerfil) {
        this.servicoPerfil = servicoPerfil;
    }

    /**
     * Retorna os dados do perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Nome, e-mail e foto
     */
    @GetMapping
    public ResponseEntity<RespostaPerfil> buscarPerfil(Authentication autenticacao) {
        return ResponseEntity.ok(servicoPerfil.buscarPerfil(autenticacao.getName()));
    }

    /**
     * Atualiza nome e e-mail do usuário autenticado (trocar o e-mail exige a senha atual e
     * devolve um token novo).
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Novo nome, novo e-mail e, se o e-mail mudar, a senha atual
     * @return Perfil atualizado
     */
    @PutMapping
    public ResponseEntity<RespostaPerfil> atualizarPerfil(Authentication autenticacao,
                                                          @Valid @RequestBody RequisicaoAtualizacaoPerfil requisicao) {
        return ResponseEntity.ok(servicoPerfil.atualizarPerfil(autenticacao.getName(), requisicao));
    }

    /**
     * Troca a senha do usuário autenticado e devolve um token novo.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Senha atual e nova senha
     * @return Perfil com o token novo
     */
    @PutMapping("/senha")
    public ResponseEntity<RespostaPerfil> trocarSenha(Authentication autenticacao,
                                                      @Valid @RequestBody RequisicaoTrocaSenha requisicao) {
        return ResponseEntity.ok(servicoPerfil.trocarSenha(autenticacao.getName(), requisicao));
    }

    /**
     * Troca a foto de perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param foto Imagem JPG, PNG ou WEBP de até 5 MB, no campo multipart "foto"
     * @return Perfil com a URL da foto nova
     */
    @PutMapping("/foto")
    public ResponseEntity<RespostaPerfil> atualizarFoto(Authentication autenticacao,
                                                        @RequestPart("foto") MultipartFile foto) {
        return ResponseEntity.ok(servicoPerfil.atualizarFoto(autenticacao.getName(), foto));
    }

    /**
     * Remove a foto de perfil do usuário autenticado.
     *
     * @param autenticacao Autenticação do usuário atual
     * @return Perfil sem foto
     */
    @DeleteMapping("/foto")
    public ResponseEntity<RespostaPerfil> removerFoto(Authentication autenticacao) {
        return ResponseEntity.ok(servicoPerfil.removerFoto(autenticacao.getName()));
    }

    /**
     * Exclui o cadastro do usuário autenticado e todos os dados dele. Exige a senha atual.
     *
     * @param autenticacao Autenticação do usuário atual
     * @param requisicao Senha atual
     * @return Resposta vazia com status 204
     */
    @DeleteMapping
    public ResponseEntity<Void> excluirCadastro(Authentication autenticacao,
                                                @Valid @RequestBody RequisicaoExclusaoCadastro requisicao) {
        servicoPerfil.excluirCadastro(autenticacao.getName(), requisicao.senha());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Rodar os testes do perfil**

Run: `./gradlew test --tests "com.efinanceiro.controlador.PerfilTeste" --no-daemon`
Expected: PASS (15 testes). Se `fotoAcimaDe5MbDevolve400` falhar com 500, conferir se o `MaxUploadSizeExceededException` não está sendo lançado antes do serviço — no MockMvc não é (ele não aplica o limite do Tomcat), então o 400 tem que vir do `validarFoto`.

- [ ] **Step 7: Rodar a suíte toda**

Run: `./gradlew clean build --no-daemon`
Expected: BUILD SUCCESSFUL, 57 testes, 0 falhas.

- [ ] **Step 8: Checkpoint** — commit fica com o Cristian.

---

### Task 7: README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Funcionalidade** — em `## Funcionalidades`, logo depois do bullet `**Recuperação de Senha:**`:

```markdown
- **Perfil do Usuário:** Troca de nome, e-mail e senha, foto de perfil (Cloudinary) e exclusão do próprio cadastro com todos os dados.
```

- [ ] **Step 2: Tecnologia** — trocar a linha `- **E-mail:** Resend (redefinição de senha)` por:

```markdown
- **Serviços Externos:** Resend (e-mail de redefinição de senha) e Cloudinary (foto de perfil)
```

- [ ] **Step 3: Variável de ambiente** — na tabela, logo depois da linha de `FRONTEND_URL`:

```markdown
| `CLOUDINARY_URL` | URL de acesso do Cloudinary, copiada do painel dele (formato `cloudinary://chave:segredo@nome`) | `cloudinary://123:abc@minha-nuvem` | Sim, para foto de perfil |
```

- [ ] **Step 4: Tabela da API** — depois da seção `### Autenticação`:

```markdown
### Perfil
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/perfil` | Nome, e-mail e foto do usuário autenticado |
| PUT | `/api/perfil` | Atualiza nome e e-mail (trocar o e-mail exige `senhaAtual` e devolve um token novo) |
| PUT | `/api/perfil/senha` | Troca a senha (`senhaAtual` + `novaSenha`); desloga os outros aparelhos e devolve um token novo |
| PUT | `/api/perfil/foto` | Envia a foto de perfil (multipart, campo `foto`: JPG, PNG ou WEBP de até 5 MB) |
| DELETE | `/api/perfil/foto` | Remove a foto de perfil |
| DELETE | `/api/perfil` | Exclui o cadastro e todos os dados do usuário (exige `{ "senha": "..." }` no corpo) |
```

- [ ] **Step 5: Checkpoint** — commit fica com o Cristian.

---

### Task 8: Front — `fotoUrl` na sessão e avatar com foto

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\login.js:16-20`
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\cadastro.js:16-20`
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\script.js` (métodos de sessão, linhas ~72-107)

- [ ] **Step 1: `login.js` e `cadastro.js`** — nos dois arquivos, trocar o `self.salvarSessao` inteiro por:

```javascript
    self.salvarSessao = function (dados) {
        localStorage.setItem('token', dados.token);
        localStorage.setItem('nome', dados.nome);
        localStorage.setItem('email', dados.email);

        if (dados.fotoUrl) {
            localStorage.setItem('fotoUrl', dados.fotoUrl);
        } else {
            localStorage.removeItem('fotoUrl');
        }
    };
```

- [ ] **Step 2: `script.js` — sessão** — logo depois de `self.obterEmail`, adicionar:

```javascript
    self.obterFotoUrl = function () {
        return localStorage.getItem('fotoUrl');
    };

    /**
     * Grava na sessão os dados que vêm da API de perfil. O token só é trocado quando a API manda
     * um novo (troca de e-mail ou de senha).
     *
     * @param {object} perfil resposta de /api/perfil ({ nome, email, fotoUrl, token })
     * @returns
     */
    self.salvarPerfilNaSessao = function (perfil) {
        localStorage.setItem('nome', perfil.nome);
        localStorage.setItem('email', perfil.email);

        if (perfil.fotoUrl) {
            localStorage.setItem('fotoUrl', perfil.fotoUrl);
        } else {
            localStorage.removeItem('fotoUrl');
        }

        if (perfil.token) {
            localStorage.setItem('token', perfil.token);
        }
    };
```

e em `self.limparSessao`, adicionar `localStorage.removeItem('fotoUrl');` depois da linha do `email`.

- [ ] **Step 3: `script.js` — avatar com foto** — trocar o `self.exibirDadosUsuario` inteiro por:

```javascript
    /**
     * Mostra a foto do usuário num avatar, ou as iniciais do nome quando não houver foto
     * (ou quando a foto não carregar).
     *
     * @param {jQuery} $avatar elemento .ef-avatar
     * @param {string} nome nome do usuário
     * @param {string|null} fotoUrl URL da foto, ou null
     * @returns
     */
    self.renderizarAvatar = function ($avatar, nome, fotoUrl) {
        var iniciais = (nome || '').split(' ').map(function (parte) { return parte.charAt(0); }).slice(0, 2).join('').toUpperCase();

        if (!fotoUrl) {
            $avatar.empty().text(iniciais || '--');
            return;
        }

        var $foto = $('<img>', { class: 'ef-avatar__img', src: fotoUrl, alt: '' })
            .on('error', function () {
                $avatar.empty().text(iniciais || '--');
            });

        $avatar.empty().append($foto);
    };

    /**
     * Exibe nome, e-mail e avatar (foto ou iniciais) do usuário logado na sidebar e na topbar mobile.
     *
     * @returns
     */
    self.exibirDadosUsuario = function () {
        var nome = self.obterNome() || '';

        $('#sidebarUserName').text(nome).attr('title', nome);
        $('#sidebarUserEmail').text(self.obterEmail() || '').attr('title', self.obterEmail() || '');
        self.renderizarAvatar($('#sidebarAvatar'), nome, self.obterFotoUrl());
        self.renderizarAvatar($('#mobileAvatar'), nome, self.obterFotoUrl());
    };
```

- [ ] **Step 4: Verificar**

Run (na pasta do front): `npm run lint:js`
Expected: sem erros.

- [ ] **Step 5: Checkpoint** — commit fica com o Cristian.

---

### Task 9: Front — markup e estilo do modal "Meu perfil"

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\index.html:69-79` (bloco do usuário na sidebar), `:100` (avatar mobile), e antes do `<!-- MODAL: NOVA RECORRÊNCIA -->`
- Modify: `D:\PROJETOS\e-financeiro-front\assets\css\style.css` (fim do arquivo)

- [ ] **Step 1: Sidebar** — trocar o bloco `<div class="ef-user-chip">…</div>` (avatar + nomes + botão sair) por:

```html
            <div class="sidebar-user">
                <button type="button" class="ef-user-chip" id="btnOpenProfile" title="Meu perfil" aria-label="Abrir meu perfil">
                    <span class="ef-avatar" id="sidebarAvatar">--</span>
                    <span class="ef-user-chip__text">
                        <span class="ef-user-chip__name" id="sidebarUserName">Carregando...</span>
                        <span class="ef-user-chip__meta" id="sidebarUserEmail"></span>
                    </span>
                </button>
                <button class="ef-icon-btn ef-icon-btn--sm icon-btn--destructive" id="btnLogoutDesktop" title="Sair" aria-label="Sair">
                    <i data-lucide="log-out" class="ef-icon ef-icon--sm"></i>
                </button>
            </div>
```

- [ ] **Step 2: Topbar mobile** — trocar a linha `<span class="ef-avatar ef-avatar--sm" id="mobileAvatar">--</span>` por:

```html
                <button type="button" class="avatar-btn" id="btnOpenProfileMobile" title="Meu perfil" aria-label="Abrir meu perfil">
                    <span class="ef-avatar ef-avatar--sm" id="mobileAvatar">--</span>
                </button>
```

- [ ] **Step 3: Modal** — inserir logo antes de `<!-- MODAL: NOVA RECORRÊNCIA -->`:

```html
    <!-- MODAL: MEU PERFIL -->
    <div class="ef-dialog ef-dialog--auto" id="modalProfile" role="dialog" aria-modal="true" aria-labelledby="modalProfileTitle" hidden>
        <div class="ef-dialog__panel">
            <span class="ef-dialog__handle" aria-hidden="true"></span>
            <div class="ef-dialog__head">
                <div class="ef-dialog__text">
                    <h2 class="ef-dialog__title" id="modalProfileTitle">Meu perfil</h2>
                    <p class="ef-dialog__description">Seus dados de acesso e sua foto.</p>
                </div>
                <button type="button" class="ef-dialog__close" aria-label="Fechar"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18" /><path d="M6 6l12 12" /></svg></button>
            </div>

            <section class="profile-photo" aria-label="Foto de perfil">
                <span class="ef-avatar profile-photo__avatar" id="profileAvatar">--</span>
                <div class="profile-photo__actions">
                    <button type="button" class="ef-btn ef-btn--secondary ef-btn--sm" id="btnChangePhoto">Trocar foto</button>
                    <button type="button" class="ef-btn ef-btn--ghost ef-btn--sm" id="btnRemovePhoto" hidden>Remover foto</button>
                    <input type="file" id="inputProfilePhoto" accept="image/jpeg,image/png,image/webp" hidden>
                    <span class="ef-field__message">JPG, PNG ou WEBP. A foto é recortada em quadrado.</span>
                </div>
            </section>

            <section class="profile-section" aria-labelledby="profileDataTitle">
                <h3 class="profile-section__title" id="profileDataTitle">Dados</h3>

                <div class="ef-field">
                    <label class="ef-field__label" for="inputProfileName">Nome</label>
                    <input class="ef-input" type="text" id="inputProfileName" autocomplete="name" maxlength="120">
                </div>

                <div class="ef-field">
                    <label class="ef-field__label" for="inputProfileEmail">E-mail</label>
                    <input class="ef-input" type="email" id="inputProfileEmail" autocomplete="email" maxlength="160">
                </div>

                <div class="ef-field" id="fieldProfileEmailPassword" hidden>
                    <label class="ef-field__label" for="inputProfileEmailPassword">Senha atual</label>
                    <input class="ef-input" type="password" id="inputProfileEmailPassword" autocomplete="current-password">
                    <span class="ef-field__message">Pra trocar o e-mail, confirme com sua senha. Os outros aparelhos serão desconectados.</span>
                </div>

                <button type="button" class="ef-btn ef-btn--primary ef-btn--block" id="btnConfirmProfileData">Salvar alterações</button>
            </section>

            <section class="profile-section" aria-labelledby="profilePasswordTitle">
                <h3 class="profile-section__title" id="profilePasswordTitle">Senha</h3>

                <div class="ef-field">
                    <label class="ef-field__label" for="inputProfileCurrentPassword">Senha atual</label>
                    <input class="ef-input" type="password" id="inputProfileCurrentPassword" autocomplete="current-password">
                </div>

                <div class="ef-field">
                    <label class="ef-field__label" for="inputProfileNewPassword">Nova senha</label>
                    <input class="ef-input" type="password" id="inputProfileNewPassword" autocomplete="new-password">
                    <span class="ef-field__message">Mínimo de 8 caracteres.</span>
                </div>

                <div class="ef-field">
                    <label class="ef-field__label" for="inputProfileConfirmPassword">Confirme a nova senha</label>
                    <input class="ef-input" type="password" id="inputProfileConfirmPassword" autocomplete="new-password">
                </div>

                <button type="button" class="ef-btn ef-btn--secondary ef-btn--block" id="btnConfirmProfilePassword">Alterar senha</button>
            </section>

            <section class="profile-section" aria-labelledby="profileDangerTitle">
                <h3 class="profile-section__title" id="profileDangerTitle">Zona de perigo</h3>
                <p class="profile-section__text">Excluir o cadastro apaga todos os seus dados do E-Financeiro.</p>

                <button type="button" class="ef-btn ef-btn--danger" id="btnDeleteProfile">Excluir meu cadastro</button>

                <div class="profile-danger-alert" id="alertDeleteProfile" role="group" aria-labelledby="alertDeleteProfileTitle" hidden>
                    <p class="profile-danger-alert__title" id="alertDeleteProfileTitle">Tem certeza que quer excluir seu cadastro?</p>
                    <p class="profile-danger-alert__text">Todos os seus dados (contas, cartões, movimentações, recorrências e foto) serão apagados permanentemente. Essa ação não pode ser desfeita.</p>

                    <div class="ef-field">
                        <label class="ef-field__label" for="inputDeleteProfilePassword">Sua senha</label>
                        <input class="ef-input" type="password" id="inputDeleteProfilePassword" autocomplete="current-password">
                    </div>

                    <div class="profile-danger-alert__actions">
                        <button type="button" class="ef-btn ef-btn--secondary" id="btnCancelDeleteProfile">Cancelar</button>
                        <button type="button" class="ef-btn ef-btn--danger" id="btnConfirmDeleteProfile">Confirmar</button>
                    </div>
                </div>
            </section>
        </div>
    </div>
```

- [ ] **Step 4: Estilos** — adicionar no fim de `assets/css/style.css`:

```css
/* ===== Meu perfil ===== */

/* Bloco do usuário na sidebar: o chip abre o perfil, o botão de sair fica ao lado */
.sidebar-user {
    display: flex;
    align-items: center;
    gap: var(--space-1);
}

/* Avatar da topbar mobile clicável */
.avatar-btn {
    display: inline-flex;
    padding: 0;
    border: none;
    border-radius: var(--radius-circle);
    background-color: transparent;
    cursor: pointer;
}

.avatar-btn:focus-visible {
    outline: none;
    box-shadow: var(--shadow-focus);
}

/* Modal mais largo que os de movimentação/cartão */
#modalProfile {
    --dialog-width: 560px;
}

.profile-photo {
    display: flex;
    align-items: center;
    gap: var(--space-4);
}

.profile-photo__avatar {
    width: 88px;
    height: 88px;
    font-size: var(--text-2xl);
}

.profile-photo__actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
}

.profile-photo__actions .ef-field__message {
    flex-basis: 100%;
}

.profile-section {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding-top: var(--space-4);
    border-top: 1px solid var(--border-subtle);
}

.profile-section__title {
    margin: 0;
    font: var(--type-body);
    font-weight: var(--weight-semibold);
}

.profile-section__text {
    margin: 0;
    font: var(--type-body-sm);
    color: var(--text-muted);
}

.profile-section .ef-btn--danger {
    align-self: flex-start;
}

/* Alerta de exclusão do cadastro: aparece dentro do modal, não é um segundo modal */
.profile-danger-alert {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    border: 1px solid var(--border-invalid);
    border-radius: var(--radius-md);
    background-color: var(--tone-negative-bg);
}

.profile-danger-alert__title {
    margin: 0;
    font: var(--type-body);
    font-weight: var(--weight-semibold);
    color: var(--tone-negative-fg);
}

.profile-danger-alert__text {
    margin: 0;
    font: var(--type-body-sm);
    color: var(--text-primary);
}

.profile-danger-alert__actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--space-2);
}
```

- [ ] **Step 5: Verificar**

Run (na pasta do front): `npm run lint:css`
Expected: sem erros. (Se a regra de token reclamar do `1px` no `border-top`/`border`, trocar por `var(--border-width, 1px)` só se esse token existir em `design-system/tokens/`; senão manter — `border` não está na lista de propriedades com token obrigatório.)

- [ ] **Step 6: Checkpoint** — commit fica com o Cristian.

---

### Task 10: Front — abrir o modal e salvar dados/senha

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\script.js` (novos métodos antes de `self.openModal`; ligações no `self.iniciar`)

- [ ] **Step 1: Métodos** — adicionar logo antes de `self.openModal = function (selector) {`:

```javascript
    /**
     * Busca o perfil na API e atualiza a sessão e a tela — nome, e-mail e foto podem ter mudado
     * em outro aparelho.
     *
     * @returns
     */
    self.carregarPerfil = function () {
        $.ajax({
            url: self.apiBaseUrl + '/api/perfil',
            headers: self.cabecalhoAuth(),
            success: function (perfil) {
                self.salvarPerfilNaSessao(perfil);
                self.exibirDadosUsuario();
                self.exibirSaudacao();
            },
            error: function (jqXHR) {
                self.tratarErroRequisicao(jqXHR);
            }
        });
    };

    /**
     * Abre o modal "Meu perfil" preenchido com os dados da sessão e com os blocos de senha e de
     * exclusão limpos.
     *
     * @returns
     */
    self.abrirModalPerfil = function () {
        $('#inputProfileName').val(self.obterNome() || '');
        $('#inputProfileEmail').val(self.obterEmail() || '');
        $('#inputProfileEmailPassword, #inputProfileCurrentPassword, #inputProfileNewPassword, #inputProfileConfirmPassword').val('');
        $('#fieldProfileEmailPassword').prop('hidden', true);

        self.esconderAlertaExclusao();
        self.renderizarFotoPerfil();
        self.openModal('#modalProfile');
    };

    /**
     * Mostra a foto (ou iniciais) no avatar do modal e o botão "Remover foto" só quando existe foto.
     *
     * @returns
     */
    self.renderizarFotoPerfil = function () {
        self.renderizarAvatar($('#profileAvatar'), self.obterNome(), self.obterFotoUrl());
        $('#btnRemovePhoto').prop('hidden', !self.obterFotoUrl());
    };

    /**
     * Mostra o campo "Senha atual" do bloco de dados só quando o e-mail digitado é diferente do atual.
     *
     * @returns
     */
    self.atualizarCampoSenhaEmail = function () {
        var emailDigitado = $('#inputProfileEmail').val().trim().toLowerCase();
        $('#fieldProfileEmailPassword').prop('hidden', emailDigitado === (self.obterEmail() || ''));
    };

    /**
     * Valida o bloco de dados do perfil (nome, e-mail e, se o e-mail mudou, a senha atual).
     *
     * @returns {boolean} true se pode enviar
     */
    self.validarDadosPerfil = function () {
        feedback.limparErros('#modalProfile');

        var nome = $('#inputProfileName').val().trim();
        var email = $('#inputProfileEmail').val().trim();
        var trocandoEmail = !$('#fieldProfileEmailPassword').prop('hidden');

        if (!nome) {
            feedback.marcarErro('#inputProfileName', 'Informe seu nome');
        }

        if (!email || email.indexOf('@') < 1) {
            feedback.marcarErro('#inputProfileEmail', 'Informe um e-mail válido');
        }

        if (trocandoEmail && !$('#inputProfileEmailPassword').val()) {
            feedback.marcarErro('#inputProfileEmailPassword', 'Informe sua senha para trocar o e-mail');
        }

        if ($('#modalProfile [aria-invalid="true"]').length > 0) {
            feedback.focarPrimeiroErro('#modalProfile');
            return false;
        }

        return true;
    };

    /**
     * Salva nome e e-mail. Se o e-mail mudou, a API devolve um token novo, que substitui o da sessão.
     *
     * @returns
     */
    self.salvarDadosPerfil = function () {
        if (!self.validarDadosPerfil()) {
            return;
        }

        var trocandoEmail = !$('#fieldProfileEmailPassword').prop('hidden');

        $.ajax({
            url: self.apiBaseUrl + '/api/perfil',
            method: 'PUT',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify({
                nome: $('#inputProfileName').val().trim(),
                email: $('#inputProfileEmail').val().trim(),
                senhaAtual: trocandoEmail ? $('#inputProfileEmailPassword').val() : null
            }),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (perfil) {
                self.salvarPerfilNaSessao(perfil);
                self.exibirDadosUsuario();
                self.exibirSaudacao();
                self.renderizarFotoPerfil();
                $('#inputProfileEmailPassword').val('');
                $('#fieldProfileEmailPassword').prop('hidden', true);
                feedback.exibirSucesso('Dados atualizados', trocandoEmail ? 'Os outros aparelhos foram desconectados.' : '');
            },
            error: function (jqXHR) {
                var senhaIncorreta = feedback.mensagemDaApi(jqXHR, 401);
                var emailEmUso = feedback.mensagemDaApi(jqXHR, 409);

                if (senhaIncorreta) {
                    $('#inputProfileEmailPassword').val('');
                    feedback.marcarErro('#inputProfileEmailPassword', senhaIncorreta);
                    feedback.focarPrimeiroErro('#modalProfile');
                } else if (emailEmUso) {
                    feedback.marcarErro('#inputProfileEmail', emailEmUso);
                    feedback.focarPrimeiroErro('#modalProfile');
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
     * Troca a senha. A API devolve um token novo (os outros aparelhos são desconectados).
     *
     * @returns
     */
    self.alterarSenhaPerfil = function () {
        feedback.limparErros('#modalProfile');

        var senhaAtual = $('#inputProfileCurrentPassword').val();
        var novaSenha = $('#inputProfileNewPassword').val();

        if (!senhaAtual) {
            feedback.marcarErro('#inputProfileCurrentPassword', 'Informe sua senha atual');
        }

        if (novaSenha.length < 8) {
            feedback.marcarErro('#inputProfileNewPassword', 'A nova senha deve ter no mínimo 8 caracteres');
        } else if (novaSenha !== $('#inputProfileConfirmPassword').val()) {
            feedback.marcarErro('#inputProfileConfirmPassword', 'As senhas não conferem');
        }

        if ($('#modalProfile [aria-invalid="true"]').length > 0) {
            feedback.focarPrimeiroErro('#modalProfile');
            return;
        }

        $.ajax({
            url: self.apiBaseUrl + '/api/perfil/senha',
            method: 'PUT',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify({ senhaAtual: senhaAtual, novaSenha: novaSenha }),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (perfil) {
                self.salvarPerfilNaSessao(perfil);
                $('#inputProfileCurrentPassword, #inputProfileNewPassword, #inputProfileConfirmPassword').val('');
                feedback.exibirSucesso('Senha alterada', 'Os outros aparelhos foram desconectados.');
            },
            error: function (jqXHR) {
                var senhaIncorreta = feedback.mensagemDaApi(jqXHR, 401);

                $('#inputProfileCurrentPassword').val('');

                if (senhaIncorreta) {
                    feedback.marcarErro('#inputProfileCurrentPassword', senhaIncorreta);
                    feedback.focarPrimeiroErro('#modalProfile');
                } else {
                    self.tratarErroRequisicao(jqXHR);
                }
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };
```

- [ ] **Step 2: Ligações no `self.iniciar`** — logo antes de `self.exibirDadosUsuario();` (perto do fim do `iniciar`):

```javascript
            $('#btnOpenProfile, #btnOpenProfileMobile').on('click', self.abrirModalPerfil);

            $('#modalProfile').on('click', function (e) {
                if ($(e.target).is('#modalProfile')) {
                    self.closeModal('#modalProfile');
                }
            });

            $('#inputProfileEmail').on('input', self.atualizarCampoSenhaEmail);
            $('#btnConfirmProfileData').on('click', self.salvarDadosPerfil);
            $('#btnConfirmProfilePassword').on('click', self.alterarSenhaPerfil);
```

e logo depois de `self.carregarRecorrencias();`:

```javascript
            self.carregarPerfil();
```

- [ ] **Step 3: Verificar**

Run (na pasta do front): `npm run lint:js`
Expected: sem erros.

- [ ] **Step 4: Checkpoint** — commit fica com o Cristian.

---

### Task 11: Front — foto de perfil (recorte no navegador e envio)

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\script.js`

- [ ] **Step 1: Métodos** — adicionar logo depois de `self.alterarSenhaPerfil`:

```javascript
    /**
     * Recorta a imagem num quadrado central de 512x512 e converte pra JPEG. Uma foto de 10 MB do
     * celular vira ~50 KB antes de ir pra API.
     *
     * @param {File} arquivo imagem escolhida pelo usuário
     * @returns {Promise<Blob>} imagem recortada
     */
    self.recortarFoto = function (arquivo) {
        return new Promise(function (resolve, reject) {
            var endereco = URL.createObjectURL(arquivo);
            var imagem = new Image();

            imagem.onload = function () {
                var lado = Math.min(imagem.naturalWidth, imagem.naturalHeight);
                var canvas = document.createElement('canvas');
                var contexto = canvas.getContext('2d');

                URL.revokeObjectURL(endereco);

                if (!contexto) {
                    reject(new Error('Canvas indisponível'));
                    return;
                }

                canvas.width = 512;
                canvas.height = 512;
                contexto.drawImage(imagem,
                    (imagem.naturalWidth - lado) / 2, (imagem.naturalHeight - lado) / 2, lado, lado,
                    0, 0, 512, 512);

                canvas.toBlob(function (recorte) {
                    if (recorte) {
                        resolve(recorte);
                    } else {
                        reject(new Error('Falha ao gerar a imagem'));
                    }
                }, 'image/jpeg', 0.85);
            };

            imagem.onerror = function () {
                URL.revokeObjectURL(endereco);
                reject(new Error('Arquivo não é uma imagem'));
            };

            imagem.src = endereco;
        });
    };

    /**
     * Valida a imagem escolhida, recorta no navegador, mostra a prévia e envia pra API. Se o envio
     * falhar, volta a mostrar a foto anterior.
     *
     * @returns
     */
    self.enviarFotoPerfil = function () {
        var arquivo = $('#inputProfilePhoto')[0].files[0];
        $('#inputProfilePhoto').val('');

        if (!arquivo) {
            return;
        }

        if (['image/jpeg', 'image/png', 'image/webp'].indexOf(arquivo.type) === -1) {
            feedback.exibirToast('negative', 'Formato não aceito', 'Escolha uma foto JPG, PNG ou WEBP.');
            return;
        }

        self.recortarFoto(arquivo)
            .then(function (recorte) {
                var dados = new FormData();
                dados.append('foto', recorte, 'foto.jpg');

                self.renderizarAvatar($('#profileAvatar'), self.obterNome(), URL.createObjectURL(recorte));
                self.mostrarCarregando();

                return $.ajax({
                    url: self.apiBaseUrl + '/api/perfil/foto',
                    method: 'PUT',
                    headers: self.cabecalhoAuth(),
                    data: dados,
                    processData: false,
                    contentType: false
                });
            })
            .then(function (perfil) {
                self.salvarPerfilNaSessao(perfil);
                self.exibirDadosUsuario();
                self.renderizarFotoPerfil();
                feedback.exibirSucesso('Foto atualizada');
            })
            .catch(function (erro) {
                self.renderizarFotoPerfil();

                if (erro && erro.status !== undefined) {
                    self.tratarErroRequisicao(erro);
                } else {
                    feedback.exibirToast('negative', 'Não foi possível usar essa imagem', 'Tente outra foto.');
                }
            })
            .finally(function () {
                self.esconderCarregando();
            });
    };

    /**
     * Remove a foto de perfil (volta a exibir as iniciais).
     *
     * @returns
     */
    self.removerFotoPerfil = function () {
        $.ajax({
            url: self.apiBaseUrl + '/api/perfil/foto',
            method: 'DELETE',
            headers: self.cabecalhoAuth(),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function (perfil) {
                self.salvarPerfilNaSessao(perfil);
                self.exibirDadosUsuario();
                self.renderizarFotoPerfil();
                feedback.exibirSucesso('Foto removida');
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

- [ ] **Step 2: Ligações no `self.iniciar`** — junto das ligações da Task 10:

```javascript
            $('#btnChangePhoto').on('click', function () {
                $('#inputProfilePhoto').trigger('click');
            });
            $('#inputProfilePhoto').on('change', self.enviarFotoPerfil);
            $('#btnRemovePhoto').on('click', self.removerFotoPerfil);
```

- [ ] **Step 3: Verificar**

Run (na pasta do front): `npm run lint:js`
Expected: sem erros.

- [ ] **Step 4: Checkpoint** — commit fica com o Cristian.

---

### Task 12: Front — exclusão do cadastro (alerta dentro do modal)

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\assets\js\script.js`

- [ ] **Step 1: Métodos** — adicionar logo depois de `self.removerFotoPerfil`:

```javascript
    /**
     * Mostra o alerta de exclusão do cadastro dentro do modal de perfil (não abre outro modal).
     *
     * @returns
     */
    self.mostrarAlertaExclusao = function () {
        $('#btnDeleteProfile').prop('hidden', true);
        $('#alertDeleteProfile').prop('hidden', false);
        $('#inputDeleteProfilePassword').val('').trigger('focus');
    };

    /**
     * Fecha o alerta de exclusão (botão Cancelar ou reabertura do modal) e limpa a senha digitada.
     *
     * @returns
     */
    self.esconderAlertaExclusao = function () {
        $('#inputDeleteProfilePassword').val('');
        $('#alertDeleteProfile').prop('hidden', true);
        $('#btnDeleteProfile').prop('hidden', false);
    };

    /**
     * Exclui o cadastro e todos os dados do usuário, depois da senha confirmada no alerta. Sucesso
     * limpa a sessão e volta pro login.
     *
     * @returns
     */
    self.excluirCadastro = function () {
        feedback.limparErros('#alertDeleteProfile');

        var senha = $('#inputDeleteProfilePassword').val();

        if (!senha) {
            feedback.marcarErro('#inputDeleteProfilePassword', 'Informe sua senha');
            feedback.focarPrimeiroErro('#alertDeleteProfile');
            return;
        }

        $.ajax({
            url: self.apiBaseUrl + '/api/perfil',
            method: 'DELETE',
            contentType: 'application/json',
            headers: self.cabecalhoAuth(),
            data: JSON.stringify({ senha: senha }),
            beforeSend: function () {
                self.mostrarCarregando();
            },
            success: function () {
                self.limparSessao();
                feedback.exibirSucesso('Cadastro excluído', 'Seus dados foram apagados.');

                // Dá tempo do Toast aparecer antes de sair da página
                setTimeout(function () {
                    window.location.href = 'login.html';
                }, 1500);
            },
            error: function (jqXHR) {
                // Senha errada volta 401 com mensagem: o erro vai no próprio campo, sem deslogar
                var senhaIncorreta = feedback.mensagemDaApi(jqXHR, 401);

                $('#inputDeleteProfilePassword').val('');

                if (senhaIncorreta) {
                    feedback.marcarErro('#inputDeleteProfilePassword', senhaIncorreta);
                    feedback.focarPrimeiroErro('#alertDeleteProfile');
                } else {
                    self.tratarErroRequisicao(jqXHR);
                }
            },
            complete: function () {
                self.esconderCarregando();
            }
        });
    };
```

- [ ] **Step 2: Ligações no `self.iniciar`** — junto das ligações da Task 10:

```javascript
            $('#btnDeleteProfile').on('click', self.mostrarAlertaExclusao);
            $('#btnCancelDeleteProfile').on('click', self.esconderAlertaExclusao);
            $('#btnConfirmDeleteProfile').on('click', self.excluirCadastro);
```

- [ ] **Step 3: Verificar**

Run (na pasta do front): `npm run lint:js`
Expected: sem erros.

- [ ] **Step 4: Checkpoint** — commit fica com o Cristian.

---

### Task 13: Front — cache do PWA e verificação completa

**Files:**
- Modify: `D:\PROJETOS\e-financeiro-front\sw.js:1-4`

- [ ] **Step 1: Subir a versão do cache** — o service worker serve HTML/JS/CSS com cache-first; sem trocar a versão, quem já instalou o app continua com o `script.js` antigo (isso também vale pra mudança de exclusão de recorrência do mesmo dia). Trocar o comentário e a constante por:

```javascript
// v5: modal "Meu perfil" (dados, senha, foto, exclusão do cadastro) e exclusão total de
// recorrência. Trocar a versão descarta o cache antigo, que serviria o CSS/JS anterior
// (cache-first).
const CACHE_NAME = 'e-financeiro-shell-v5';
```

- [ ] **Step 2: Verificação estática completa**

Run (na pasta do front): `npm run check`
Expected: exit 0 (typecheck + ESLint + Stylelint sem erros; os avisos do `lint:design` sobre tokens órfãos já existiam).

- [ ] **Step 3: Verificação manual no navegador** — com o backend rodando local (`./gradlew bootRun` com `CLOUDINARY_URL` configurada) e o front em `npx http-server -p 5501 .` (trocar `self.apiBaseUrl` pra `http://localhost:8080` só durante o teste e voltar depois):
  1. Entrar, clicar no bloco do usuário na sidebar → modal abre mais largo, com nome/e-mail preenchidos.
  2. Mudar só o nome → "Dados atualizados", sidebar e saudação mudam, não pede senha.
  3. Digitar outro e-mail → aparece "Senha atual"; senha errada → erro no campo, continua logado; certa → e-mail muda.
  4. Trocar foto (uma foto grande do celular) → prévia na hora, avatar da sidebar/topbar com a foto; "Remover foto" volta às iniciais.
  5. Alterar senha com confirmação diferente → erro no campo; certa → "Senha alterada".
  6. "Excluir meu cadastro" → alerta aparece dentro do modal; Cancelar fecha; senha errada → erro no campo; Confirmar com a senha → Toast e volta pro login; login com o e-mail antigo falha.
  7. Repetir o passo 1 com a largura de celular (DevTools, 375px): o avatar da topbar abre o modal como folha de baixo.

- [ ] **Step 4: Checkpoint** — commit fica com o Cristian (front e back vão pro ar juntos).

---

### Task 14: Obsidian

**Files:**
- Modify: `D:\Coisas\Jarvis\Projetos\e-financeiro\Decisoes.md` (nova entrada no topo)
- Modify: `D:\Coisas\Jarvis\Projetos\e-financeiro\Decisoes-Frontend.md` (nova entrada no topo)
- Modify: `D:\Coisas\Jarvis\Projetos\e-financeiro\Visao-Geral.md` (Funcionalidades + Stack)

- [ ] **Step 1: `Decisoes.md`** — entrada `## 2026-09-26 — Perfil do usuário (dados, foto, exclusão do cadastro)` com: foto no Cloudinary enviada pelo backend (chave só no Render, `public_id` fixo por usuário = sem órfãs); "Excluir meu cadastro" (nome diferente de "Excluir conta", que é conta bancária); exclusão em lote + foto apagada só `afterCommit`; troca de e-mail/senha exige senha atual, limite 5/15min por usuário, e devolve token novo; tipo da foto conferido pelos bytes iniciais. Pendência: verificação do novo e-mail por link continua fora do escopo.

- [ ] **Step 2: `Decisoes-Frontend.md`** — entrada com: modal `#modalProfile` mais largo (`--dialog-width: 560px`) em vez de página nova; alerta de exclusão dentro do modal (não Dialog empilhado); recorte 512x512 no `<canvas>` antes do envio; cache do PWA `v5` (e o lembrete: **toda mudança de JS/CSS precisa subir a versão do `sw.js`**).

- [ ] **Step 3: `Visao-Geral.md`** — em Funcionalidades, adicionar "Perfil do usuário (nome, e-mail, senha, foto no Cloudinary e exclusão do cadastro)"; em Stack do back-end, "Cloudinary (foto de perfil)"; em "Como rodar", mencionar `CLOUDINARY_URL`.
