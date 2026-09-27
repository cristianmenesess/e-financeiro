-- Momento da última troca de senha: tokens JWT emitidos antes disso deixam de valer
ALTER TABLE usuarios ADD COLUMN senha_alterada_em TIMESTAMP;

-- Busca de e-mail sem diferenciar maiúsculas/minúsculas (login, cadastro, esqueci a senha)
CREATE INDEX idx_usuarios_email_minusculo ON usuarios (lower(email));

-- Número da parcela passa a ser gravado na geração, em vez de calculado pela data (que o
-- usuário pode editar). Parcelas existentes recebem o mesmo número que a API já exibia,
-- calculado pela distância em meses entre o início da recorrência e a data da parcela.
ALTER TABLE transacoes ADD COLUMN numero_parcela INT;

UPDATE transacoes t
SET numero_parcela = ((EXTRACT(YEAR FROM t.data_transacao) - EXTRACT(YEAR FROM r.data_inicio)) * 12
                     + (EXTRACT(MONTH FROM t.data_transacao) - EXTRACT(MONTH FROM r.data_inicio)) + 1)::INT
FROM recorrencias r
WHERE t.recorrencia_id = r.id;
