# E-Financeiro API

Um back-end robusto para controle financeiro pessoal, focado em boas práticas, código limpo e arquitetura. Este projeto fornece a API para o gerenciamento de entradas, saídas e cartões, servindo de base para um front-end web. 

O desenvolvimento priorizou práticas de mercado, como o uso rigoroso de DTOs para evitar exposição de entidades, tratamento de valores monetários com `BigDecimal` de ponta a ponta e autenticação stateless.

**Acesse o site:** https://e-financeiro.vercel.app/
*(Nota: Hospedado no Render no plano gratuito. A primeira requisição pode levar até 60 segundos para "acordar" o servidor).*

---

## Tecnologias

- **Linguagem e Framework:** Java 17 + Spring Boot
- **Banco de Dados:** PostgreSQL (hospedado no Neon)
- **Integração com o Banco:** Spring Data JPA + Migrations com Flyway
- **Segurança:** Spring Security + JWT
- **Validação:** Bean Validation nos DTOs
- **Build:** Gradle (Groovy DSL) + Lombok

---

## Funcionalidades

- **Autenticação:** Cadastro de usuários e login seguro retornando token JWT.
- **Gestão de Cartões:** CRUD completo com cálculo automático do gasto mensal baseado nas transações associadas.
- **Controle de Transações:** Registro de receitas e despesas com filtros por tipo de conta (Pessoa Física / Jurídica).
- **Resumo Financeiro:** Endpoint dedicado para entregar o balanço atualizado (saldo, total de entradas e saídas).

---

## Rodando Localmente

Para testar o projeto, você precisará do Java 17 e de um banco PostgreSQL rodando. 

1. Clone o repositório.
2. Configure as seguintes variáveis de ambiente (você pode criar um arquivo `.env` ou configurar na sua IDE):

| Variável | Descrição | Exemplo | Obrigatória |
|---|---|---|---|
| `DB_URL` | URL JDBC do Postgres | `jdbc:postgresql://host/db?sslmode=require` | Sim |
| `DB_USUARIO` | Usuário do banco | `admin` | Sim |
| `DB_SENHA` | Senha do banco | `senha123` | Sim |
| `JWT_SECRET` | Segredo base64 para o JWT | `seu-segredo-de-256-bits-aqui` | Sim |
| `SPRING_PROFILES_ACTIVE` | Ambiente ativo | `local` (Padrão) | Não |
| `CORS_ORIGENS_PERMITIDAS` | Origens aceitas | `http://localhost:5501` | Não |

3. Execute o comando abaixo (o Flyway criará as tabelas automaticamente):
```bash
./gradlew bootRun
```

---

## Visão Geral da API

Todas as rotas (exceto autenticação) exigem o envio do header: `Authorization: Bearer <seu_token>`.

### Autenticação
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/autenticacao/cadastro` | Cria um novo usuário |
| POST | `/api/autenticacao/login` | Autentica e retorna um token JWT |

### Cartões
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/cartoes` | Lista os cartões do usuário autenticado |
| POST | `/api/cartoes` | Cria um cartão |
| PUT | `/api/cartoes/{id}` | Atualiza um cartão |
| DELETE | `/api/cartoes/{id}` | Exclui um cartão |

### Transações
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/transacoes?conta=todas\|cpf\|pj` | Lista as transações |
| GET | `/api/transacoes/resumo?conta=todas\|cpf\|pj` | Saldo, total de entradas e total de saídas |
| POST | `/api/transacoes` | Cria uma transação |
| PUT | `/api/transacoes/{id}` | Atualiza uma transação |
| DELETE | `/api/transacoes/{id}` | Exclui uma transação |
