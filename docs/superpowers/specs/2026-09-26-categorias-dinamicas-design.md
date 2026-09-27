# Categorias dinâmicas (fixas do sistema + personalizadas por usuário)

## Contexto

Hoje a categoria é um `enum` Java (`Categoria`: `RENDA`, `DESPESA`, `ALIMENTACAO`, `MORADIA`, `OUTRO`) gravado como texto na coluna `categoria` de `transacoes` e `recorrencias`. O front repete a mesma lista fixa no `script.js` (`categoriasDisponiveis`) e mapeia ícone/cor de cada uma em `resolveIconeCategoria`. Categoria só existe na prática pra saída: na entrada o campo some e o front manda `RENDA` automaticamente.

O usuário quer que cada pessoa possa criar as próprias categorias, mantendo um conjunto fixo do sistema.

Decisões já alinhadas com o usuário:
1. **Tabela única `categorias`** com as fixas dentro dela (`usuario_id` nulo) e as personalizadas (`usuario_id` do dono). Uma única FK `categoria_id` em `transacoes` e `recorrencias`. Descartado: manter o enum pras fixas + tabela só pras personalizadas (duas colunas possíveis por transação, `if` espalhado em listagem, filtro, gráfico e recorrências).
2. **Categorias de entrada e de saída.** Cada categoria tem `tipo`. Na entrada passam a aparecer chips ("Renda" pré-selecionada; o usuário pode criar "Salário", "Freelance"...).
3. **Excluir uma personalizada move** as movimentações e recorrências dela pra fixa genérica do mesmo tipo — **Outro** (saída) ou **Renda** (entrada). Nada se perde.
4. **Seção "Categorias" no menu**, no padrão de Contas/Cartões/Recorrências.
5. **Ícone de uma lista curada de ícones Lucide e cor entre os tons do design system** (sem cor hex solta — funciona em tema claro/escuro e respeita o lint do front).

## Modelo de dados

Migration `V8__categorias_dinamicas.sql`:

```sql
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios (id),   -- NULL = categoria fixa do sistema
    codigo VARCHAR(20) UNIQUE,                    -- só nas fixas
    nome VARCHAR(40) NOT NULL,
    tipo VARCHAR(10) NOT NULL,                    -- ENTRADA | SAIDA
    icone VARCHAR(40) NOT NULL,
    tom VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_categorias_usuario_id ON categorias (usuario_id);

INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES
    ('RENDA',       'Renda',       'ENTRADA', 'banknote',     'positive'),
    ('DESPESA',     'Despesa',     'SAIDA',   'receipt',      'negative'),
    ('ALIMENTACAO', 'Alimentação', 'SAIDA',   'shopping-bag', 'warning'),
    ('MORADIA',     'Moradia',     'SAIDA',   'house',        'brand'),
    ('OUTRO',       'Outro',       'SAIDA',   'ellipsis',     'neutral');

-- transacoes e recorrencias: nova FK preenchida a partir do texto atual, depois o texto sai
ALTER TABLE transacoes ADD COLUMN categoria_id BIGINT REFERENCES categorias (id);
UPDATE transacoes t SET categoria_id = c.id FROM categorias c WHERE c.codigo = t.categoria;
UPDATE transacoes SET categoria_id = (SELECT id FROM categorias WHERE codigo = 'OUTRO') WHERE categoria_id IS NULL;
ALTER TABLE transacoes ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE transacoes DROP COLUMN categoria;
CREATE INDEX idx_transacoes_categoria_id ON transacoes (categoria_id);
-- (mesmo bloco pra recorrencias)
```

- O `codigo` existe pro código achar "Renda" e "Outro" sem depender de id fixo.
- A linha "valor desconhecido → OUTRO" é só uma rede de segurança (o enum não permitia outros valores).
- Registros antigos com tipo e categoria "misturados" (a API antiga permitia) não são alterados.

Entidade `Categoria` (substitui o enum de mesmo nome): `id`, `usuario` (`@ManyToOne` opcional), `codigo`, `nome`, `tipo` (`TipoTransacao`), `icone`, `tom`, `criadoEm`. `Transacao` e `Recorrencia` trocam o campo enum por `@ManyToOne(optional = false) Categoria categoria` (`categoria_id`).

## Ícones e tons permitidos

Definidos uma única vez no backend (`OpcoesCategoria`) e expostos pela API, pro front montar os seletores sem duplicar a lista:

- **Tons (6):** `brand`, `positive`, `negative`, `warning`, `ai`, `neutral` — os que têm classe `ef-icon-tile--<tom>` no design system (`neutral` = tile padrão). `info` fica de fora porque não tem classe de tile.
- **Ícones (Lucide):** os 5 das fixas (`banknote`, `receipt`, `shopping-bag`, `house`, `ellipsis`) + `car`, `fuel`, `bus`, `plane`, `heart-pulse`, `pill`, `graduation-cap`, `book-open`, `gamepad-2`, `film`, `music`, `shirt`, `dog`, `baby`, `gift`, `dumbbell`, `utensils`, `coffee`, `smartphone`, `wifi`, `zap`, `wrench`, `briefcase`, `laptop`, `piggy-bank`, `trending-up`, `landmark`, `hand-coins`.

## API

Novo `ControladorCategoria`, `@RequestMapping("/api/categorias")`:

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| GET | `/api/categorias` | — | 200 lista de `RespostaCategoria` (fixas primeiro, depois personalizadas por nome) |
| GET | `/api/categorias/opcoes` | — | 200 `{ icones: [...], tons: [...] }` |
| POST | `/api/categorias` | `RequisicaoCategoria` | 201 `RespostaCategoria` |
| PUT | `/api/categorias/{id}` | `RequisicaoAtualizacaoCategoria` | 200 `RespostaCategoria` |
| DELETE | `/api/categorias/{id}` | — | 200 `{ movidas: N }` |

```java
record RespostaCategoria(Long id, String nome, TipoTransacao tipo, String icone, String tom, boolean fixa) {}
record RequisicaoCategoria(@NotBlank @Size(max = 40) String nome, @NotNull TipoTransacao tipo,
                           @NotBlank String icone, @NotBlank String tom) {}
record RequisicaoAtualizacaoCategoria(@NotBlank @Size(max = 40) String nome,
                                      @NotBlank String icone, @NotBlank String tom) {}
record RespostaExclusaoCategoria(long movidas) {}
record RespostaOpcoesCategoria(List<String> icones, List<String> tons) {}
```

Regras:
- **Nome** com `trim()`; não pode repetir (ignorando maiúsculas/minúsculas) nenhum nome de categoria fixa nem outra categoria do mesmo usuário → 409 "Já existe uma categoria com esse nome". Na edição, o próprio registro não conta como repetido.
- **Ícone/tom** fora da lista → 400 "Ícone inválido" / "Cor inválida" (`DadosInvalidosException`).
- **Tipo não muda na edição** (não existe no DTO de atualização): trocar entrada↔saída deixaria as movimentações dela com o tipo errado.
- **Limite de 50 personalizadas por usuário** → 422 "Limite de 50 categorias personalizadas atingido" (`RegraDeNegocioException`).
- **Fixas e categorias de outro usuário**: PUT/DELETE devolvem 404 "Categoria não encontrada" (mesmo padrão de posse de conta/cartão — não confirma que o id existe).
- **Excluir** (`@Transactional`): move as transações e recorrências da categoria pra fixa genérica do mesmo tipo (`OUTRO` pra saída, `RENDA` pra entrada) com `UPDATE` em lote, apaga a categoria, devolve quantas linhas foram movidas (transações + recorrências).

**Contrato de transações e recorrências (muda — front e back sobem juntos):**
- `RequisicaoTransacao` e `RequisicaoRecorrencia`: `Categoria categoria` → `@NotNull Long categoriaId` ("A categoria é obrigatória").
- `RespostaTransacao` e `RespostaRecorrencia`: `Categoria categoria` → `Long categoriaId, String nomeCategoria`.
- Categoria inexistente/de outro usuário → 404 "Categoria não encontrada". Categoria de tipo diferente do lançamento → 400 "A categoria não é do mesmo tipo da movimentação".
- `BuscadorRecursosDoUsuario` ganha `buscarCategoria(categoriaId, usuarioId)` (aceita fixas e as do usuário) — usada por `ServicoTransacao`, `ServicoRecorrencia` e `ServicoCategoria`.
- `@EntityGraph` das listagens de transações e recorrências passa a incluir `categoria` (sem N+1).

**Exclusão do cadastro (perfil):** `ServicoPerfil.excluirCadastro` passa a apagar as categorias do usuário, na ordem transações → recorrências → **categorias** → cartões → contas → usuário. As fixas não são tocadas.

## Front-end

**Seção "Categorias"** (`data-page="categorias"`, ícone `tags`, depois de Recorrências no menu lateral e na navegação mobile):
- Duas listas, **Entradas** e **Saídas**, no mesmo visual de linha das Recorrências (tile colorido com o ícone + nome). Fixas com um cadeado (`lock`) e sem ações; personalizadas com editar e excluir.
- Botão **"Nova categoria"** abre `#modalCategoria` (tamanho padrão): nome, tipo (Entrada/Saída — só na criação; na edição fica desabilitado), grade de ícones, seletor de tom, prévia ao vivo do tile. Ícones e tons vêm de `GET /api/categorias/opcoes`.
- **Excluir** usa `feedback.confirmar`, informando quantas movimentações e recorrências vão pra "Outro"/"Renda" (contadas nos dados já carregados no front). Sucesso recarrega categorias, transações e recorrências e mostra o `movidas` devolvido pela API.

**Modais de movimentação e de recorrência:** chips montados de `state.categorias` filtrados pelo tipo. Entrada → chips visíveis com "Renda" pré-selecionada; saída → nenhum selecionado, escolha obrigatória (como hoje). Envio passa a mandar `categoriaId`.

**Troca do código fixo:** `categoriasDisponiveis` e o mapa de `resolveIconeCategoria` saem. Tudo que exibe categoria resolve pelo `categoriaId` na lista carregada (`{ classe: 'ef-icon-tile--' + tom, cor: 'var(--tone-' + tom + '-accent)', icone }`, com `neutral` → tile padrão): lista do dashboard, chips de filtro do dashboard, recorrências, gráfico "Gastos por categoria" (agrupa as saídas por `categoriaId`; a antiga exclusão explícita de `RENDA` vira consequência natural do filtro por tipo SAIDA). Categoria desconhecida (ex.: excluída em outro aparelho) cai no visual de "Outro".

**Carregamento:** categorias carregam no início junto com as contas; chips, filtros e gráfico são montados depois que as categorias chegam.

**PWA:** `sw.js` → `e-financeiro-shell-v6`.

## Testes

Backend (Testcontainers):
- Migration: teste que roda o Flyway programaticamente num schema separado do mesmo Postgres de teste até a V7, insere usuário/conta/transações/recorrência com o texto antigo (`'MORADIA'`, `'RENDA'`...), aplica a V8 e confere que cada linha aponta pra categoria fixa certa, que a coluna texto sumiu e que as 5 fixas existem com os códigos certos.
- CRUD: criar (201), nome repetido com fixa ou com outra personalizada, inclusive por maiúsculas (409), ícone/tom inválido (400), 51ª categoria (422), editar (tipo não muda), fixa não edita nem exclui (404), categoria de outro usuário (404).
- Transação/recorrência: com categoria de outro usuário (404), com categoria de tipo diferente (400), com categoria personalizada (200, resposta traz `categoriaId`/`nomeCategoria`).
- Exclusão: move transações e recorrências pra Outro/Renda, devolve `movidas` certo, saldo do resumo não muda.
- Perfil: excluir cadastro apaga as categorias do usuário e mantém as 5 fixas.
- Testes existentes que mandam `"categoria": "..."` passam a mandar `categoriaId` da fixa correspondente (mudança de contrato, não enfraquecimento).

Front: `npm run check` + teste de ponta a ponta no navegador contra backend local.

## Fora do escopo

- Reordenar categorias manualmente; esconder uma categoria fixa; subcategorias.
- Trocar o tipo de uma categoria existente.
