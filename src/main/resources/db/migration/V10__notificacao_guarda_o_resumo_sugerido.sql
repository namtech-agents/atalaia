-- V10: o resumo que a IA sugere para o aviso ao cliente (US16). É só um rascunho: a pessoa do escritório lê,
-- corrige e aprova antes de gerar o link. Fica vazio se a IA não respondeu, se o documento foi para revisão
-- ou se a sugestão não serviu (citava data, prazo ou link). 600: o mesmo limite do resumo do aviso (V9)

alter table notificacao add column resumo_sugerido varchar(600);
