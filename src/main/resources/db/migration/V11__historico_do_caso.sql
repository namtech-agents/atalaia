-- V11: o histórico do caso. Cada linha é uma coisa que aconteceu com uma notificação: quando, quem e o quê.
-- A tabela só recebe linhas novas: nada aqui é alterado nem apagado pelo Atalaia. Refazer a conta do prazo,
-- por exemplo, troca a data na notificação, mas a conta anterior continua contada aqui

create table evento_do_caso (
    id             bigint generated always as identity primary key,
    notificacao_id bigint not null references notificacao (id),
    quando         timestamp with time zone not null,
    -- O e-mail de quem fez, ou "Cliente, pelo link". Vazio nos eventos dos casos de antes desta migração
    quem           varchar(254),
    -- A frase pronta, como aparece na tela ("Calculou o prazo: data limite 09/12/2026")
    acao           varchar(300) not null
);
create index evento_do_caso_notificacao_idx on evento_do_caso (notificacao_id);

-- Os casos que já existiam ganham o histórico que dá para tirar das colunas: só o último cálculo do prazo
-- (os anteriores não foram guardados) e sem quem enviou nem quem resolveu (também não eram guardados)
insert into evento_do_caso (notificacao_id, quando, quem, acao)
select id, recebida_em, null, 'Enviou o PDF' from notificacao;

insert into evento_do_caso (notificacao_id, quando, quem, acao)
select id, prazo_calculado_em, prazo_calculado_por, 'Calculou o prazo'
from notificacao where prazo_calculado_em is not null;

insert into evento_do_caso (notificacao_id, quando, quem, acao)
select notificacao_id, criado_em, criado_por, 'Gerou o link para o cliente' from aviso_ao_cliente;

insert into evento_do_caso (notificacao_id, quando, quem, acao)
select notificacao_id, confirmado_em, 'Cliente, pelo link', 'Confirmou a ciência'
from aviso_ao_cliente where confirmado_em is not null;

insert into evento_do_caso (notificacao_id, quando, quem, acao)
select id, resolvida_em, null, 'Resolveu o caso. Desfecho: ' || case desfecho
    when 'ARQUIVADO' then 'Arquivado' when 'MULTA_REDUZIDA' then 'Multa reduzida'
    when 'MULTA_CHEIA' then 'Multa cheia' else 'Outro' end
from notificacao where resolvida_em is not null;
