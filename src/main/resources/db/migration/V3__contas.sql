CREATE TABLE contas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    nome VARCHAR(120) NOT NULL,
    cor_fundo VARCHAR(7) NOT NULL,
    cor_texto VARCHAR(7) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_contas_usuario_id ON contas (usuario_id);
