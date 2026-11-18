-- V5: o banco só aceita os quatro desfechos que existem no Java (Desfecho).
-- O Java já garante isso; a restrição protege contra quem escrever direto no banco.
-- Desfecho novo no Java? Cria o V seguinte trocando esta restrição, senão o banco recusa o valor novo
-- (o teste bancoAceitaTodosOsDesfechosDaLista avisa se alguém esquecer)

-- Sem valor (null) continua valendo: é o caso ainda aberto. Texto vazio ('') é recusado
alter table notificacao add constraint notificacao_desfecho_valido check (
    desfecho in ('ARQUIVADO', 'MULTA_REDUZIDA', 'MULTA_CHEIA', 'OUTRO')
);
