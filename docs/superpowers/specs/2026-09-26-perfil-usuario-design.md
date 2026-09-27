# Perfil do usuário (dados, foto e exclusão do cadastro)

## Contexto

Hoje o bloco do usuário na sidebar (avatar com iniciais + nome + e-mail) e o avatar da topbar mobile são só texto: clicar neles não faz nada. O usuário quer:
- abrir uma tela de atualização do cadastro clicando no próprio perfil;
- poder colocar uma foto de perfil;
- poder excluir o próprio cadastro, com um alerta de confirmação (Confirmar / Cancelar), apagando todos os dados vinculados a ele.

Decisões já alinhadas com o usuário:
1. **Modal, não página nova.** `modalProfile` dentro do `index.html`, mais largo que os modais de movimentação/cartão (`width` ~560px no desktop; bottom sheet no mobile, como os demais).
2. **Nome da ação: "Excluir meu cadastro".** "Excluir conta" já significa apagar uma conta bancária (tela Contas) — usar o mesmo nome confundiria.
3. **Confirmação da exclusão é um alerta dentro do próprio modal**, não um segundo modal empilhado: caixa com aviso, campo de senha e botões **Confirmar** / **Cancelar**.
4. **A exclusão exige a senha atual**, mesmo padrão da exclusão de conta bancária — é a ação mais destrutiva do sistema e não tem volta.
5. **Campos editáveis: nome, e-mail e senha** (além da foto).
6. **Foto no Cloudinary**, enviada **pelo backend** (o navegador nunca fala com o Cloudinary nem conhece a chave).

## Modelo de dados

Migration `V7__foto_perfil.sql`:

```sql
ALTER TABLE usuarios ADD COLUMN foto_url VARCHAR(500);
```

`Usuario` ganha `String fotoUrl` (`@Column(name = "foto_url", length = 500)`), nulo quando não há foto.

O identificador da foto no Cloudinary é derivado do id do usuário — `efinanceiro/usuarios/{id}` — então não precisa de coluna própria. Upload sempre com esse mesmo `public_id` e `overwrite=true`: foto nova substitui a antiga, nunca sobra foto órfã por troca. A `fotoUrl` gravada é a `secure_url` devolvida pelo Cloudinary, que já inclui a versão (`/v123456/`), então o navegador não fica com a foto antiga em cache.

## API

Novo `ControladorPerfil`, `@RequestMapping("/api/perfil")`. Sempre age sobre o usuário logado (`autenticacao.getName()`), nunca recebe id na URL — não existe como mexer no perfil de outro usuário.

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/perfil` | — | 200 `RespostaPerfil` |
| PUT | `/api/perfil` | `RequisicaoAtualizacaoPerfil` | 200 `RespostaPerfil` (com `token` novo se o e-mail mudou) |
| PUT | `/api/perfil/senha` | `RequisicaoTrocaSenha` | 200 `RespostaPerfil` (com `token` novo) |
| PUT | `/api/perfil/foto` | multipart, campo `foto` | 200 `RespostaPerfil` |
| DELETE | `/api/perfil/foto` | — | 200 `RespostaPerfil` (`fotoUrl` nulo) |
| DELETE | `/api/perfil` | `RequisicaoExclusaoCadastro` | 204 |

### DTOs

```java
record RespostaPerfil(String nome, String email, String fotoUrl, String token) {}
// token só vem preenchido quando um token novo foi emitido (troca de e-mail ou de senha); senão null

record RequisicaoAtualizacaoPerfil(
    @NotBlank @Size(max = 120) String nome,
    @NotBlank @Email @Size(max = 160) String email,
    @Size(max = 200) String senhaAtual   // obrigatória só se o e-mail mudar (validado no serviço)
) {}

record RequisicaoTrocaSenha(
    @NotBlank @Size(max = 200) String senhaAtual,
    @NotBlank @Size(min = 8) @TamanhoMaximoEmBytes(72) String novaSenha
) {}

record RequisicaoExclusaoCadastro(@NotBlank @Size(max = 200) String senha) {}
```

`RespostaAutenticacao` (login/cadastro) ganha o campo `fotoUrl` — campo a mais, não quebra o contrato atual.

### Regras

**Atualizar nome/e-mail (`PUT /api/perfil`):**
- Nome gravado com `trim()`.
- E-mail normalizado (`trim().toLowerCase()`, mesma regra do cadastro). Se for igual ao atual, é só atualização de nome — não pede senha, não emite token.
- Se o e-mail mudou: `senhaAtual` obrigatória (400 "Informe a senha atual para trocar o e-mail" se vier vazia), senha errada → 401 "Senha incorreta", e-mail já usado por outro usuário (`existsByEmailIgnoreCase`) → 409 "Já existe um usuário cadastrado com esse e-mail". Sucesso → grava e devolve um token novo (o subject do JWT é o e-mail).
- Consequência aceita: as outras sessões caem (os tokens antigos têm o e-mail velho; o filtro não encontra mais o usuário → 401 sem corpo).

**Trocar senha (`PUT /api/perfil/senha`):**
- Senha atual errada → 401 "Senha incorreta".
- Sucesso → grava o hash novo e `senhaAlteradaEm = agora` (regra já existente: tokens emitidos antes deixam de valer) e devolve um token novo pra sessão atual.

**Foto (`PUT /api/perfil/foto`):**
- Aceita `image/jpeg`, `image/png`, `image/webp`; outro tipo → 400 "A foto deve ser JPG, PNG ou WEBP". Arquivo vazio → 400.
- Limite de 5 MB (`spring.servlet.multipart.max-file-size=5MB`, `max-request-size=6MB`); acima disso → 400 "A foto deve ter no máximo 5 MB" (handler de `MaxUploadSizeExceededException` no `TratadorGlobalDeExcecoes`).
- Envio ao Cloudinary com `allowed_formats: jpg,png,webp` (o Cloudinary recusa arquivo que não é imagem de verdade, mesmo com Content-Type forjado), `public_id` fixo, `overwrite: true`, `invalidate: true`, transformação `c_fill,g_face,w_512,h_512`.
- Falha do Cloudinary → log de erro + 502 "Não foi possível salvar a foto agora. Tente de novo." (nova exceção `ServicoExternoIndisponivelException`); a `fotoUrl` antiga continua.

**Remover foto (`DELETE /api/perfil/foto`):** apaga no Cloudinary (`destroy` com `invalidate`) e grava `fotoUrl = null`. Se o usuário não tem foto, só responde 200.

**Excluir cadastro (`DELETE /api/perfil`):** `@Transactional`:
1. Senha errada → 401 "Senha incorreta" (com mensagem: o front mostra no campo e **não** desloga — regra do front "só desloga em 401 sem mensagem").
2. Apaga em lote (um `DELETE` por tabela, `@Modifying @Query`), nesta ordem por causa das FKs: transações → recorrências → cartões → contas → usuário.
3. Registra um `TransactionSynchronization.afterCommit` que apaga a foto no Cloudinary — só depois que o banco confirmou. Falha do Cloudinary aqui só gera log de erro (a foto fica órfã no Cloudinary; os dados já foram apagados).
4. 204.

**Limite de tentativas:** nova regra `SENHA_ATUAL_POR_USUARIO` no `ServicoLimiteRequisicoes` — 5 tentativas a cada 15 min por usuário, consumida em toda ação que confere a senha atual (trocar e-mail, trocar senha, excluir cadastro). Impede usar essas rotas pra adivinhar a senha com um token roubado. Excedido → 429.

## Componentes do backend

- `controlador/ControladorPerfil` — fino, padrão dos demais.
- `servico/ServicoPerfil` — regras acima; usa `BuscadorRecursosDoUsuario`, `ServicoJwt`, `PasswordEncoder`, `ServicoLimiteRequisicoes`, `ServicoFotoPerfil`, `Clock`.
- `servico/ServicoFotoPerfil` — única classe que conhece o SDK do Cloudinary (`enviar(usuarioId, arquivo)` → URL; `apagar(usuarioId)`). Mockada nos testes (`@MockitoBean`), como o `ServicoEmail`.
- `configuracao/ConfiguracaoCloudinary` — bean `Cloudinary` a partir de `app.cloudinary.url=${CLOUDINARY_URL:}`.
- Repositórios: `deleteByUsuarioId` em lote em `RepositorioTransacao`, `RepositorioRecorrencia`, `RepositorioCartao`, `RepositorioConta`.
- Normalização de e-mail extraída de `ServicoAutenticacao` pra um lugar compartilhado (usada pelos dois serviços).
- Dependência: `com.cloudinary:cloudinary-http5` (SDK oficial).
- Nova variável de ambiente: `CLOUDINARY_URL` (formato `cloudinary://<chave>:<segredo>@<nome-da-nuvem>`, copiado do painel do Cloudinary). README atualizado.

## Front-end

Seguindo o padrão "Tela" e a convenção de ids em inglês (`modal<Substantivo>` / `btnConfirm<Substantivo>`):

- `#sidebarUserChip` (bloco do usuário) e `#mobileAvatar` viram `<button>` que chamam `self.abrirModalPerfil()`. O botão de sair continua separado.
- `#modalProfile` (Dialog com `width` maior), em quatro blocos:
  1. **Foto** — `Avatar` grande (componente do design system, com `src` e fallback de iniciais), botões "Trocar foto" (abre `<input type="file" accept="image/jpeg,image/png,image/webp" hidden>`) e "Remover foto" (só aparece com foto). Antes de enviar, o navegador recorta a imagem num quadrado central de 512×512 com `<canvas>` e gera JPEG (~50 KB) — o envio fica leve mesmo com foto de 10 MB do celular. Prévia na hora; se o upload falhar, volta pra foto anterior.
  2. **Dados** — nome e e-mail; o campo "Senha atual" só aparece quando o e-mail digitado é diferente do atual. Botão "Salvar alterações".
  3. **Senha** — senha atual, nova senha, confirmação (validação local: mínimo 8, confirmação igual). Botão "Alterar senha".
  4. **Zona de perigo** — botão "Excluir meu cadastro"; ao clicar, aparece a caixa de alerta dentro do modal: texto "Todos os seus dados (contas, cartões, movimentações, recorrências e foto) serão apagados permanentemente. Essa ação não pode ser desfeita.", campo de senha e botões **Cancelar** (fecha a caixa e limpa a senha) e **Confirmar** (`#btnConfirmDeleteProfile`, estilo destrutivo).
- Sessão: `salvarSessao` passa a guardar `fotoUrl`; novo `obterFotoUrl`. Quando a API devolve `token`, ele substitui o da sessão.
- `exibirDadosUsuario` passa a mostrar a foto nos avatares da sidebar e da topbar quando existir (senão, iniciais como hoje).
- Ao carregar o `index.html`, `GET /api/perfil` atualiza nome/e-mail/foto na sessão (podem ter mudado em outro aparelho).
- Exclusão concluída → limpa a sessão, Toast "Cadastro excluído" e redireciona pro `login.html`.
- `login.js`/`cadastro.js` guardam a `fotoUrl` que vem no login/cadastro.
- Erros: 401 com mensagem ("Senha incorreta") aparece no campo de senha do bloco correspondente (`feedback.mensagemDaApi`); demais erros no Toast (`feedback.exibirErroAjax`).

## Testes

Backend (Testcontainers + `ServicoFotoPerfil` e `ServicoEmail` mockados, mesmo esquema da auditoria):
- GET perfil devolve nome/e-mail/foto.
- Atualizar só o nome não pede senha e não emite token.
- Trocar e-mail: sem senha → 400; senha errada → 401 com mensagem; e-mail de outro usuário (inclusive só com maiúsculas diferentes) → 409; sucesso → token novo funciona e o antigo dá 401 sem corpo.
- Trocar senha: senha atual errada → 401; sucesso → token antigo dá 401, token novo funciona, login com a senha nova funciona.
- Foto: tipo não aceito → 400; arquivo vazio → 400; acima de 5 MB → 400; sucesso grava a URL devolvida pelo serviço; falha do Cloudinary → 502 e a URL antiga continua; remover → `fotoUrl` nulo.
- Excluir cadastro: senha errada → 401 e nada é apagado; sucesso → 204, o usuário não loga mais, não sobra nenhuma linha dele em nenhuma tabela, **os dados de outro usuário continuam intactos**, e a foto é apagada no Cloudinary só depois do commit.
- 6ª tentativa de senha atual errada em 15 min → 429.

Front: `npm run check` (typecheck + ESLint + Stylelint).

## Fora do escopo

- Verificação do novo e-mail por link (o cadastro também não verifica hoje).
- Período de carência / "desfazer" a exclusão do cadastro.
- Recorte manual da foto (o recorte é automático, quadrado central; o Cloudinary ainda centraliza no rosto com `g_face`).
