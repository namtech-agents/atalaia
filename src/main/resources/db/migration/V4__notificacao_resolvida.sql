-- V4: a notificação pode ser marcada como resolvida, com o desfecho e uma observação curta.

-- Fica vazia enquanto o caso está aberto. É ela que diz se a notificação está resolvida
alter table notificacao add column resolvida_em timestamp with time zone;
-- Um dos quatro desfechos (ARQUIVADO, MULTA_REDUZIDA, MULTA_CHEIA, OUTRO)
alter table notificacao add column desfecho varchar(20);
-- O que a pessoa escreveu ao resolver
alter table notificacao add column observacao varchar(500);

-- Ou os três campos estão vazios (caso aberto), ou os três estão preenchidos (caso resolvido)
alter table notificacao add constraint notificacao_resolvida_por_inteiro check (
    (resolvida_em is null and desfecho is null and observacao is null)
    or (resolvida_em is not null and desfecho is not null and observacao is not null)
);
