-- V2: a notificação passa a guardar o PDF original.
-- Sem ele, um PDF escaneado (sem texto) não teria como ser revisto por uma pessoa.
-- A tabela ainda está vazia em todo lugar, por isso a coluna já nasce obrigatória.
alter table notificacao add column arquivo bytea not null;
