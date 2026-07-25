ALTER TABLE usuarios
    ADD COLUMN token_redefinicao_hash VARCHAR(64),
    ADD COLUMN token_redefinicao_expira_em TIMESTAMP;
