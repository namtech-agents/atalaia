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
    -- A posição do dado na lista, para a tela mostrar na mesma ordem em que a IA devolveu
    ordem          integer not null,
    campo          text,
    valor          text,
    trecho         text
);

-- O que foi recebido antes desta versão nunca passou pelo Analista: não pode aparecer como conferido
update notificacao
   set precisa_de_revisao = true,
       motivo_da_revisao  = 'Recebida antes da análise automática.';

-- A tela busca os dados de uma notificação
create index dado_extraido_notificacao_idx on dado_extraido (notificacao_id);
