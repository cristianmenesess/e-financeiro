-- Categorias deixam de ser um enum fixo: fixas do sistema (usuario_id nulo) + personalizadas por usuário
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios (id),
    codigo VARCHAR(20) UNIQUE,
    nome VARCHAR(40) NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    icone VARCHAR(40) NOT NULL,
    tom VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_categorias_usuario_id ON categorias (usuario_id);

-- Ordem importa: os testes contam com RENDA=1 ... OUTRO=5 num banco novo
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('RENDA', 'Renda', 'ENTRADA', 'banknote', 'positive');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('DESPESA', 'Despesa', 'SAIDA', 'receipt', 'negative');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('ALIMENTACAO', 'Alimentação', 'SAIDA', 'shopping-bag', 'warning');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('MORADIA', 'Moradia', 'SAIDA', 'house', 'brand');
INSERT INTO categorias (codigo, nome, tipo, icone, tom) VALUES ('OUTRO', 'Outro', 'SAIDA', 'ellipsis', 'neutral');

-- Transações: FK preenchida a partir do texto antigo; qualquer valor desconhecido vira OUTRO
ALTER TABLE transacoes ADD COLUMN categoria_id BIGINT REFERENCES categorias (id);
UPDATE transacoes t SET categoria_id = c.id FROM categorias c WHERE c.codigo = t.categoria;
UPDATE transacoes SET categoria_id = (SELECT id FROM categorias WHERE codigo = 'OUTRO') WHERE categoria_id IS NULL;
ALTER TABLE transacoes ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE transacoes DROP COLUMN categoria;
CREATE INDEX idx_transacoes_categoria_id ON transacoes (categoria_id);

-- Recorrências: mesma conversão
ALTER TABLE recorrencias ADD COLUMN categoria_id BIGINT REFERENCES categorias (id);
UPDATE recorrencias r SET categoria_id = c.id FROM categorias c WHERE c.codigo = r.categoria;
UPDATE recorrencias SET categoria_id = (SELECT id FROM categorias WHERE codigo = 'OUTRO') WHERE categoria_id IS NULL;
ALTER TABLE recorrencias ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE recorrencias DROP COLUMN categoria;
CREATE INDEX idx_recorrencias_categoria_id ON recorrencias (categoria_id);
