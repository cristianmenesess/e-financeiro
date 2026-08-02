DELETE FROM transacoes;

ALTER TABLE transacoes DROP COLUMN conta;
ALTER TABLE transacoes ADD COLUMN conta_id BIGINT NOT NULL REFERENCES contas (id);

CREATE INDEX idx_transacoes_conta_id ON transacoes (conta_id);
