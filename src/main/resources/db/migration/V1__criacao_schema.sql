CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE cartoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    nome VARCHAR(120) NOT NULL,
    cor_fundo VARCHAR(7) NOT NULL,
    cor_texto VARCHAR(7) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_cartoes_usuario_id ON cartoes (usuario_id);

CREATE TABLE transacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    descricao VARCHAR(160) NOT NULL,
    valor NUMERIC(12, 2) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    conta VARCHAR(10) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    cartao_id BIGINT REFERENCES cartoes (id) ON DELETE SET NULL,
    data_transacao DATE NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_transacoes_usuario_id ON transacoes (usuario_id);
CREATE INDEX idx_transacoes_cartao_id ON transacoes (cartao_id);
