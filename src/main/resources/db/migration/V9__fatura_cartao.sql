-- Ciclo da fatura: compras no cartão passam a cair no vencimento da fatura
ALTER TABLE cartoes ADD COLUMN dia_fechamento INTEGER NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31);
ALTER TABLE cartoes ADD COLUMN dia_vencimento INTEGER NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31);

-- Data em que a compra foi feita; nula quando não há cartão (aí a data da transação é a própria data)
ALTER TABLE transacoes ADD COLUMN data_compra DATE;
