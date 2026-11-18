-- V5: o banco só aceita os quatro desfechos que existem no Java (Desfecho).
-- O Java já garante isso; a restrição protege contra quem escrever direto no banco.
-- Desfecho novo no Java? Cria o V seguinte trocando esta restrição, senão o banco recusa o valor novo

-- Vazio continua valendo: é o caso ainda aberto (um "in" com valor vazio não reprova a linha)
alter table notificacao add constraint notificacao_desfecho_valido check (
    desfecho in ('ARQUIVADO', 'MULTA_REDUZIDA', 'MULTA_CHEIA', 'OUTRO')
);
