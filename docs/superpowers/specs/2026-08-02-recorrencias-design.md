# Recorrências (parcelas e gastos/entradas fixas)

## Contexto

Hoje, toda `Transacao` é lançada uma de cada vez. O usuário quer um jeito de cadastrar de uma vez algo que se repete por N meses — uma compra parcelada (ex: geladeira em 10x) ou um gasto/entrada fixa (ex: aluguel, assinatura, salário) — sem precisar lançar mês a mês manualmente.

Decisões já alinhadas com o usuário:
1. **Gerar tudo de uma vez, no cadastro.** Nada de job agendado gerando transações mês a mês — o Render free tier dorme quando ocioso, então um `@Scheduled` não seria confiável. As N transações (uma por mês, a partir de uma data de início) são criadas de uma só vez, já com datas futuras.
2. **Parcelado e recorrente são a mesma coisa por baixo.** Não há um N natural pra "recorrente sem fim" (aluguel, assinatura), então o usuário escolhe a quantidade de parcelas também nesse caso — hoje ele pensa em cadastrar recorrências por 12 meses e recadastrar depois. Isso significa que parcelado e recorrente usam exatamente o mesmo mecanismo (quantidade de parcelas + data de início), diferindo só na intenção/copy da UI. Uma única entidade `Recorrencia` cobre os dois casos.
3. **As transações geradas ficam vinculadas** à `Recorrencia` de origem (FK opcional em `Transacao`), permitindo mostrar progresso ("5 de 12"), cancelar as parcelas futuras em lote, e editar o valor das parcelas futuras em lote.
4. **Suporta ENTRADA e SAÍDA** (aluguel/assinatura são saída, salário é entrada) — reaproveita o enum `TipoTransacao` que já existe.
5. **Sem exclusão total da recorrência.** Só existe "cancelar parcelas futuras" (`dataTransacao >= hoje`) — as parcelas passadas ficam como histórico, e o registro da `Recorrencia` nunca é apagado (é leve, serve de referência).
6. **Editar valor propaga só pras futuras.** Reajuste de aluguel, por exemplo: atualiza o valor de referência da `Recorrencia` e de todas as transações com `dataTransacao >= hoje`; as passadas não mudam.
7. **Editar/excluir uma transação individual continua funcionando normalmente** pelos endpoints que já existem (`PUT`/`DELETE /api/transacoes/{id}`) — eles não tocam no campo `recorrencia`, então o vínculo não quebra.
8. **Página dedicada "Recorrências"** no front, mesmo padrão de Contas/Cartões — não cabe só num checkbox do modal de nova movimentação, porque precisa de um lugar pra gerenciar (ver progresso, cancelar futuras, editar valor) depois de criado.

## Modelo de dados

Nova entidade `Recorrencia`:

```java
Long id;
Usuario usuario;        // @ManyToOne(optional=false)
String descricao;       // NOT NULL, length 160 (igual Transacao.descricao)
BigDecimal valor;       // valor de cada parcela, NOT NULL
TipoTransacao tipo;     // ENTRADA/SAIDA, enum já existente
Categoria categoria;    // enum já existente
Conta conta;            // @ManyToOne(optional=false)
Cartao cartao;          // @ManyToOne(optional=true)
Integer totalParcelas;  // NOT NULL, mínimo 1
LocalDate dataInicio;   // NOT NULL
Instant criadoEm;
```

`Transacao` ganha um campo novo:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "recorrencia_id")
private Recorrencia recorrencia;   // opcional — null pra transações lançadas manualmente
```

### Migration (`V5__recorrencias.sql`)

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

ALTER TABLE transacoes ADD COLUMN recorrencia_id BIGINT REFERENCES recorrencias (id) ON DELETE SET NULL;

CREATE INDEX idx_transacoes_recorrencia_id ON transacoes (recorrencia_id);
```

(`ON DELETE SET NULL` espelha o que já existe pra `cartao_id` em `transacoes` — se uma `Recorrencia` algum dia for apagada por fora da API, as transações não ficam órfãs/quebradas, só perdem o vínculo.)

### Geração das parcelas

No `POST`, o serviço cria a `Recorrencia` e, num loop de `0` até `totalParcelas - 1`, cria uma `Transacao` por iteração com `dataTransacao = dataInicio.plusMonths(i)` (o `LocalDate.plusMonths` do Java já lida certo com meses de tamanhos diferentes — dia 31/01 vira 28 ou 29/02 automaticamente). Toda a operação (a `Recorrencia` + as N transações) roda dentro de uma única transação de banco (`@Transactional`), atômica — ou cria tudo, ou nada.

## API

Novo `ControladorRecorrencia`, mesmo padrão de `ControladorConta`/`ControladorCartao`:

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/recorrencias` | Lista as recorrências do usuário autenticado |
| POST | `/api/recorrencias` | Cria a recorrência e gera as N transações |
| DELETE | `/api/recorrencias/{id}` | Cancela as parcelas futuras (`dataTransacao >= hoje`) — mantém a recorrência e as parcelas passadas |
| PUT | `/api/recorrencias/{id}/valor` | Propaga um novo valor pras parcelas futuras e atualiza o valor de referência da recorrência |

### DTOs

```java
public record RequisicaoRecorrencia(
        @NotBlank String descricao,
        @NotNull @DecimalMin("0.01") BigDecimal valor,
        @NotNull TipoTransacao tipo,
        @NotNull Categoria categoria,
        @NotNull Long contaId,
        Long cartaoId,
        @NotNull @Min(1) Integer totalParcelas,
        LocalDate dataInicio   // opcional, default hoje se omitido
) {}

public record RequisicaoAtualizacaoValorRecorrencia(
        @NotNull @DecimalMin("0.01") BigDecimal valor
) {}

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
        Integer parcelasRestantes,   // contagem de transações com dataTransacao >= hoje
        LocalDate dataInicio
) {}
```

### Serviço

`ServicoRecorrencia` (mesmo padrão de `ServicoConta`/`ServicoTransacao`, `@Transactional` na classe, `readOnly=true` no método de listagem):
- `listarRecorrencias(emailUsuario)` — busca as recorrências do usuário, calcula `parcelasRestantes` por uma query de contagem (`RepositorioTransacao.countByRecorrenciaIdAndDataTransacaoGreaterThanEqual`).
- `criarRecorrencia(emailUsuario, requisicao)` — resolve conta/cartão (reaproveitando a mesma resolução que `ServicoTransacao` já faz), cria a `Recorrencia`, gera as N transações.
- `cancelarFuturas(emailUsuario, id)` — apaga as transações da recorrência com `dataTransacao >= hoje` (`RepositorioTransacao.deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual`).
- `atualizarValorFuturo(emailUsuario, id, novoValor)` — busca as transações futuras da recorrência, atualiza o valor de cada uma e o valor de referência da `Recorrencia`.

Novos métodos em `RepositorioTransacao`: `countByRecorrenciaIdAndDataTransacaoGreaterThanEqual`, `deleteByRecorrenciaIdAndDataTransacaoGreaterThanEqual`, `findByRecorrenciaIdAndDataTransacaoGreaterThanEqual`.

### Erros

Reaproveita tudo que já existe — nenhum handler novo:
- Conta/cartão/recorrência não encontrados ou não pertencem ao usuário → `RecursoNaoEncontradoException` (404).
- Validação de campos (valor, quantidade de parcelas, etc.) → 400, mapa de campo→mensagem (Bean Validation).

## Front-end

- **Nova página "Recorrências"** no sidebar/bottom-nav (mesmo padrão de Contas/Cartões): `#pageRecorrencias` com `#recorrenciasGrid`, botão "Nova recorrência".
- **Card de recorrência**: descrição, valor, badge de progresso ("5 de 12" — `totalParcelas - parcelasRestantes` já decorridas), conta/cartão vinculados, tipo (Entrada/Saída). Duas ações:
  - **Editar valor** — modal pequeno com um texto de apoio deixando claro que só afeta as parcelas futuras ("As parcelas passadas não mudam") + o campo de novo valor, `PUT /api/recorrencias/{id}/valor`.
  - **Cancelar parcelas futuras** — `confirm()` simples (não precisa de senha como a exclusão de conta, porque só afeta o que ainda não aconteceu, não apaga histórico).
- **Modal "Nova recorrência"**: descrição, valor por parcela, toggle Entrada/Saída (mesmo padrão do modal de nova movimentação), categoria (chips, só aparece pra Saída — mesma regra que já existe hoje), conta (select), cartão (select, opcional, só aparece pra Saída), quantidade de parcelas (número), data de início (default hoje).

## Fora de escopo

- Exclusão total da recorrência (incluindo parcelas passadas) — só cancelamento das futuras.
- Job/geração automática de novas parcelas além das criadas no cadastro (ex: renovar sozinho depois de 12 meses) — o usuário recadastra manualmente quando quiser continuar.
- Indicador visual, na lista normal de transações do dashboard, de que uma transação faz parte de uma recorrência — hoje só é visível entrando na página Recorrências.
- Frequências diferentes de mensal (semanal, anual, etc.).
- Testes automatizados — sem suíte de testes por convenção já estabelecida no projeto.

## Testes

Sem suíte de testes automatizados, seguindo a convenção já estabelecida no restante do projeto.
