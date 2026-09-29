# E-Financeiro API

Um back-end robusto para controle financeiro pessoal, focado em boas práticas, código limpo e arquitetura. Este projeto fornece a API para o gerenciamento de contas, entradas, saídas, cartões e recorrências (parcelas e gastos/entradas fixas), servindo de base para um front-end web.

O desenvolvimento priorizou práticas de mercado, como o uso rigoroso de DTOs para evitar exposição de entidades, tratamento de valores monetários com `BigDecimal` de ponta a ponta, autenticação stateless, agregações feitas no banco (sem somar em memória) e testes de integração contra um PostgreSQL real.

**Acesse o site:** https://e-financeiro.vercel.app/

---

## Tecnologias

- **Linguagem e Framework:** Java 17 + Spring Boot
- **Banco de Dados:** PostgreSQL (hospedado no Neon)
- **Integração com o Banco:** Spring Data JPA + Migrations com Flyway
- **Segurança:** Spring Security + JWT, limite de tentativas com Bucket4j
- **Validação:** Bean Validation nos DTOs
- **Serviços Externos:** Resend (e-mail de redefinição de senha) e Cloudinary (foto de perfil)
- **Testes:** JUnit 5 + MockMvc + Testcontainers (PostgreSQL)
- **Build:** Gradle (Groovy DSL) + Lombok + Docker

---

## Funcionalidades

- **Autenticação:** Cadastro de usuários e login seguro retornando token JWT, com e-mail sem diferenciar maiúsculas/minúsculas.
- **Recuperação de Senha:** Link de redefinição por e-mail, de uso único e válido por 1 hora; a troca de senha invalida os tokens de acesso antigos.
- **Perfil do Usuário:** Troca de nome, e-mail e senha, foto de perfil (Cloudinary) e exclusão do próprio cadastro com todos os dados.
- **Proteção contra Força Bruta:** Limite de tentativas por IP e por e-mail no login, cadastro e redefinição de senha.
- **Gestão de Contas:** Contas criadas pelo usuário (nome e cor); todo cadastro já nasce com a conta "Pessoal", e a última conta não pode ser excluída.
- **Gestão de Cartões:** CRUD completo com cálculo automático do gasto do mês baseado nas transações associadas.
- **Controle de Transações:** Registro de receitas e despesas com filtro por conta e paginação opcional.
- **Categorias:** 5 categorias fixas do sistema (Renda, Despesa, Alimentação, Moradia, Outro) + categorias de entrada e saída criadas por cada usuário, com ícone e cor.
- **Recorrências:** Compras parceladas e gastos/entradas fixas geram todas as parcelas de uma vez; o valor das parcelas futuras pode ser reajustado.
- **Resumo Financeiro:** Endpoint dedicado para entregar o balanço atualizado (saldo, total de entradas e saídas), sem contar parcelas futuras.

---

## Rodando Localmente

Para testar o projeto, você precisará do Java 17 e de um banco PostgreSQL rodando. Para os testes automatizados, basta ter o Docker rodando (o banco de teste sobe sozinho via Testcontainers).

1. Clone o repositório.
2. Configure as seguintes variáveis de ambiente (você pode criar um arquivo `.env` ou configurar na sua IDE):

| Variável | Descrição | Exemplo | Obrigatória |
|---|---|---|---|
| `DB_URL` | URL JDBC do Postgres | `jdbc:postgresql://host/db?sslmode=require` | Sim |
| `DB_USUARIO` | Usuário do banco usado pela aplicação | `efinanceiro_app` | Sim |
| `DB_SENHA` | Senha do banco | `senha123` | Sim |
| `DB_FLYWAY_USUARIO` | Usuário dono do banco, usado só pelas migrations (padrão: `DB_USUARIO`) | `neondb_owner` | Não |
| `DB_FLYWAY_SENHA` | Senha do usuário das migrations (padrão: `DB_SENHA`) | `senha-do-dono` | Não |
| `JWT_SECRET` | Segredo de assinatura do JWT: texto com no mínimo 32 caracteres (usado como bytes UTF-8, não é decodificado de base64) | `um-segredo-longo-com-32-caracteres-ou-mais` | Sim |
| `JWT_EXPIRACAO_MS` | Validade do token em milissegundos | `86400000` (24h, padrão) | Não |
| `RESEND_API_KEY` | Chave da API do Resend, para enviar o e-mail de redefinição de senha | `re_xxxxxxxx` | Sim, para recuperação de senha |
| `EMAIL_REMETENTE` | Remetente dos e-mails | `onboarding@resend.dev` (padrão) | Não |
| `FRONTEND_URL` | Endereço do front-end, usado no link do e-mail de redefinição | `http://localhost:5501` (padrão) | Não |
| `CLOUDINARY_URL` | URL de acesso do Cloudinary, copiada do painel dele (formato `cloudinary://chave:segredo@nome`) | `cloudinary://123:abc@minha-nuvem` | Sim, para foto de perfil |
| `APP_FUSO_HORARIO` | Fuso usado para calcular "hoje" (saldo, parcelas, gasto do mês) | `America/Sao_Paulo` (padrão) | Não |
| `SPRING_PROFILES_ACTIVE` | Ambiente ativo | `local` (Padrão) | Não |
| `CORS_ORIGENS_PERMITIDAS` | Origens aceitas | `http://localhost:5501` | Não |

3. Execute o comando abaixo (o Flyway criará as tabelas automaticamente):
```bash
./gradlew bootRun
```

4. Para rodar os testes (precisa do Docker rodando):
```bash
./gradlew test
```

---

## Visão Geral da API

Todas as rotas (exceto autenticação) exigem o envio do header: `Authorization: Bearer <seu_token>`. Erros seguem o formato `{ "mensagem": "...", "timestamp": "..." }`, exceto erros de validação dos campos (`400`), que devolvem `{ "campo": "mensagem" }`. Token ausente, inválido ou expirado devolve `401` sem corpo.

### Autenticação
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/autenticacao/cadastro` | Cria um novo usuário (e a conta "Pessoal") e retorna um token JWT |
| POST | `/api/autenticacao/login` | Autentica e retorna um token JWT |
| POST | `/api/autenticacao/esqueci-senha` | Envia o link de redefinição por e-mail (sempre responde 200, exista o e-mail ou não) |
| POST | `/api/autenticacao/redefinir-senha` | Define a nova senha a partir do token recebido por e-mail |

### Perfil
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/perfil` | Nome, e-mail e foto do usuário autenticado |
| PUT | `/api/perfil` | Atualiza nome e e-mail (trocar o e-mail exige `senhaAtual` e devolve um token novo) |
| PUT | `/api/perfil/senha` | Troca a senha (`senhaAtual` + `novaSenha`); desloga os outros aparelhos e devolve um token novo |
| PUT | `/api/perfil/foto` | Envia a foto de perfil (multipart, campo `foto`: JPG, PNG ou WEBP de até 5 MB) |
| DELETE | `/api/perfil/foto` | Remove a foto de perfil |
| DELETE | `/api/perfil` | Exclui o cadastro e todos os dados do usuário (exige `{ "senha": "..." }` no corpo) |

### Contas
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/contas` | Lista as contas do usuário autenticado |
| POST | `/api/contas` | Cria uma conta |
| PUT | `/api/contas/{id}` | Atualiza uma conta |
| DELETE | `/api/contas/{id}` | Exclui a conta com suas transações e recorrências (exige `{ "senha": "..." }` no corpo; a última conta não pode ser excluída) |

### Cartões
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/cartoes` | Lista os cartões do usuário autenticado, com o gasto do mês |
| POST | `/api/cartoes` | Cria um cartão |
| PUT | `/api/cartoes/{id}` | Atualiza um cartão |
| DELETE | `/api/cartoes/{id}` | Exclui um cartão (as transações dele continuam, sem cartão) |

### Transações
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/transacoes?contaId={id}&pagina={n}&tamanho={n}` | Lista as transações (filtro por conta e paginação opcionais; paginado, o total vem no header `X-Total-Count`) |
| GET | `/api/transacoes/resumo?contaId={id}` | Saldo, total de entradas e total de saídas até hoje (conta opcional) |
| POST | `/api/transacoes` | Cria uma transação (com categoriaId de uma categoria do mesmo tipo) |
| PUT | `/api/transacoes/{id}` | Atualiza uma transação |
| DELETE | `/api/transacoes/{id}` | Exclui uma transação |

### Recorrências
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/recorrencias` | Lista as recorrências, com as parcelas restantes |
| POST | `/api/recorrencias` | Cria uma recorrência e gera todas as parcelas mensais |
| PUT | `/api/recorrencias/{id}/valor` | Reajusta o valor das parcelas futuras (as de hoje e as passadas não mudam) |
| DELETE | `/api/recorrencias/{id}` | Exclui a recorrência com todas as parcelas, inclusive as passadas |

### Categorias
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/categorias` | Categorias fixas do sistema + as personalizadas do usuário |
| GET | `/api/categorias/opcoes` | Ícones e cores permitidos para uma categoria |
| POST | `/api/categorias` | Cria uma categoria personalizada (nome, tipo, ícone e cor; até 50 por usuário) |
| PUT | `/api/categorias/{id}` | Edita nome, ícone e cor (o tipo não muda) |
| DELETE | `/api/categorias/{id}` | Exclui a categoria e move as movimentações dela para "Outro" (saída) ou "Renda" (entrada) |
