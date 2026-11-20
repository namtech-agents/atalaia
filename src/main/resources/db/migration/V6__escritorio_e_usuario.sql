-- V6: login. Cada pessoa entra com e-mail e senha e pertence a um escritório;
-- cada empresa passa a ser de um escritório, e ninguém vê as empresas de outro.

-- Escritório: o escritório de contabilidade que usa o Atalaia
create table escritorio (
    id   bigint generated always as identity primary key,
    nome varchar(200) not null
);

-- Usuário: quem entra no sistema
create table usuario (
    id            bigint generated always as identity primary key,
    escritorio_id bigint not null references escritorio (id),
    -- Sempre em minúsculas (o Java garante): "Ana@x.com" e "ana@x.com" são a mesma conta
    email         varchar(254) not null,
    -- Nunca a senha: só o embaralhado dela (bcrypt), que não dá para desfazer
    senha         varchar(100) not null,
    criado_em     timestamp with time zone not null default current_timestamp,
    constraint usuario_email_unico unique (email)
);
create index usuario_escritorio_idx on usuario (escritorio_id);

-- A empresa ganha o dono. Primeiro sem obrigar, para dar tempo de preencher as que já existem
alter table empresa add column escritorio_id bigint references escritorio (id);

-- Empresas que já existiam (as de exemplo da prévia) vão para um escritório sem usuário:
-- continuam no banco, mas ninguém entra nele. Num banco novo, nada disso acontece
insert into escritorio (nome)
select 'Escritório de antes do login' where exists (select 1 from empresa);
update empresa set escritorio_id = (select min(id) from escritorio) where escritorio_id is null;

alter table empresa alter column escritorio_id set not null;
create index empresa_escritorio_idx on empresa (escritorio_id);

-- O CNPJ deixa de ser único no sistema inteiro: a mesma empresa pode ser cliente de dois escritórios.
-- Dentro de um escritório continua sem repetir
alter table empresa drop constraint empresa_cnpj_unico;
alter table empresa add constraint empresa_cnpj_unico_no_escritorio unique (escritorio_id, cnpj);
