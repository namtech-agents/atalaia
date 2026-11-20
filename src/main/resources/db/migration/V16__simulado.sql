-- US30: notificação de teste (simulado), para medir quanto tempo a equipe leva para ver e avisar o cliente.
-- aberta_em: a primeira vez que alguém do escritório abriu o caso (só é marcada no simulado)
alter table notificacao add column simulado boolean not null default false;
alter table notificacao add column aberta_em timestamp with time zone;
-- Quem soltou o simulado (e-mail): a abertura dessa pessoa não conta, ela já sabe que é teste
alter table notificacao add column solto_por varchar(255);
