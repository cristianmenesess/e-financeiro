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
CREATE INDEX idx_recorrencias_conta_id ON recorrencias (conta_id);
CREATE INDEX idx_recorrencias_cartao_id ON recorrencias (cartao_id);

ALTER TABLE transacoes ADD COLUMN recorrencia_id BIGINT REFERENCES recorrencias (id) ON DELETE SET NULL;

CREATE INDEX idx_transacoes_recorrencia_id ON transacoes (recorrencia_id);
