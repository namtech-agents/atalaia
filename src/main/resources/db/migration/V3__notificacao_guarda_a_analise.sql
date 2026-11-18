-- V3: a notificação passa a guardar o que o Analista entendeu e por que foi para revisão.

-- Um dos cinco tipos (CITACAO, INTIMACAO...). Fica vazio quando a IA não classificou
alter table notificacao add column tipo varchar(20);
-- Um motivo por linha. Vazio quando não precisa de revisão
alter table notificacao add column motivo_da_revisao text;

-- Dado extraído: cada dado que o Analista tirou do documento, com o trecho de onde saiu.
-- As três colunas aceitam vazio de propósito: o que a IA devolver é guardado como veio,
-- e quem rejeita dado ruim é o Verificador (com o motivo), não o banco (com um erro)
create table dado_extraido (
    notificacao_id bigint not null references notificacao (id),
    campo          text,
    valor          text,
    trecho         text
);

-- A tela busca os dados de uma notificação
create index dado_extraido_notificacao_idx on dado_extraido (notificacao_id);
