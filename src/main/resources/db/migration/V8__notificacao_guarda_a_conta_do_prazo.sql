-- V8: a notificação guarda a conta do prazo. A data limite (data_limite, da V1) passa a ser preenchida
-- quando uma pessoa informa a ciência e escolhe a regra, e a Calculadora faz a conta.

-- O dia da ciência, informado por quem calculou
alter table notificacao add column ciencia date;
-- A conta passo a passo, um passo por linha, com a regra e o artigo. Fica copiada aqui, e não ligada à regra:
-- se a regra for excluída ou trocada depois, o que foi mostrado à pessoa continua igual
alter table notificacao add column conta_do_prazo text;
-- Quem pediu a conta (o e-mail da conta) e quando
alter table notificacao add column prazo_calculado_em timestamp with time zone;
alter table notificacao add column prazo_calculado_por varchar(254);

-- Ou ninguém calculou (os quatro vazios, e sem data limite), ou os quatro estão preenchidos.
-- A data limite pode ficar vazia mesmo com a conta feita: é quando a Calculadora manda o caso para revisão
alter table notificacao add constraint notificacao_prazo_por_inteiro check (
    (ciencia is null and conta_do_prazo is null and prazo_calculado_em is null and prazo_calculado_por is null
        and data_limite is null)
    or (ciencia is not null and conta_do_prazo is not null and prazo_calculado_em is not null
        and prazo_calculado_por is not null)
);
