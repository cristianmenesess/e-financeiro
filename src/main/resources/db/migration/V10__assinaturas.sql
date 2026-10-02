-- Assinaturas: cobrança mensal ou anual sem data de fim, até ser cancelada
CREATE TABLE assinaturas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id),
    conta_id BIGINT NOT NULL REFERENCES contas (id),
    cartao_id BIGINT REFERENCES cartoes (id) ON DELETE SET NULL,
    categoria_id BIGINT NOT NULL REFERENCES categorias (id),
    descricao VARCHAR(160) NOT NULL,
    valor NUMERIC(12, 2) NOT NULL,
    periodicidade VARCHAR(10) NOT NULL,
    data_inicio DATE NOT NULL,
    -- Data da última cobrança já lançada; as próximas são lançadas aos poucos
    gerada_ate DATE,
    cancelada_em DATE,
    -- Trava otimista: login e tarefa agendada podem lançar cobranças ao mesmo tempo
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_assinaturas_usuario_id ON assinaturas (usuario_id);
CREATE INDEX idx_assinaturas_conta_id ON assinaturas (conta_id);
CREATE INDEX idx_assinaturas_cartao_id ON assinaturas (cartao_id);
CREATE INDEX idx_assinaturas_categoria_id ON assinaturas (categoria_id);

ALTER TABLE transacoes ADD COLUMN assinatura_id BIGINT REFERENCES assinaturas (id) ON DELETE SET NULL;

CREATE INDEX idx_transacoes_assinatura_id ON transacoes (assinatura_id);
