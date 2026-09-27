-- =============================================================================
-- Usuário restrito da aplicação (menor privilégio)
-- =============================================================================
-- Rodar UMA vez, manualmente, no SQL Editor do Neon, conectado como o usuário
-- DONO do banco (o que o Flyway usou até hoje — no Neon costuma ser
-- "neondb_owner"). NÃO é uma migration do Flyway de propósito: cria login com
-- senha, e senha não pode ir pro repositório.
--
-- Depois de rodar:
--   - o usuário dono continua sendo o do Flyway (cria/altera tabelas);
--   - a aplicação passa a usar "efinanceiro_app", que só lê e grava dados —
--     não consegue DROP, ALTER, TRUNCATE nem CREATE.
--
-- Antes de rodar, troque:
--   <SENHA_FORTE>  → senha gerada (o Neon exige senha forte pra role criada via SQL)
--   <BANCO>        → nome do banco (ex: neondb)
--   <DONO>         → usuário dono atual das tabelas (ex: neondb_owner)
-- =============================================================================

CREATE ROLE efinanceiro_app WITH LOGIN PASSWORD '<SENHA_FORTE>';

GRANT CONNECT ON DATABASE <BANCO> TO efinanceiro_app;
GRANT USAGE ON SCHEMA public TO efinanceiro_app;

-- Tabelas e sequences que já existem (V1..V5)
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO efinanceiro_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO efinanceiro_app;

-- Tabelas e sequences que o Flyway criar em migrations futuras (V6 em diante)
-- recebem as mesmas permissões automaticamente, sem precisar rodar isso de novo
ALTER DEFAULT PRIVILEGES FOR ROLE <DONO> IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO efinanceiro_app;
ALTER DEFAULT PRIVILEGES FOR ROLE <DONO> IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO efinanceiro_app;

-- O histórico do Flyway só interessa ao Flyway — a aplicação não precisa nem ler
REVOKE ALL ON TABLE flyway_schema_history FROM efinanceiro_app;

-- Garante que ninguém além do dono cria objetos no schema public
-- (já é o padrão no Postgres 15+, aqui só deixa explícito)
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- =============================================================================
-- Conferência: deve listar só SELECT/INSERT/UPDATE/DELETE pras tabelas do sistema
-- =============================================================================
SELECT table_name, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS permissoes
FROM information_schema.role_table_grants
WHERE grantee = 'efinanceiro_app'
GROUP BY table_name
ORDER BY table_name;
