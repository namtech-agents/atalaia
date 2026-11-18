-- V1: o mínimo que o Marco 2 usa. Arquivo que entrou na main não se edita: mudou? cria o V2.

-- Empresa: o cliente do escritório, dono da notificação
create table empresa (
    id        bigint generated always as identity primary key,
    -- 14 posições em texto: o CNPJ novo (2026) pode ter letras
    cnpj      varchar(14)  not null unique check (char_length(cnpj) = 14),
    nome      varchar(200) not null
);

-- Notificação: um PDF enviado, com o que o Leitor e a Calculadora devolveram
create table notificacao (
    id                 bigint generated always as identity primary key,
    empresa_id         bigint not null references empresa (id),
    -- Texto que o Leitor tirou do PDF
    texto              text not null,
    -- Fica vazia enquanto o prazo não passa pelas conferências
    data_limite        date,
    precisa_de_revisao boolean not null,
    recebida_em        timestamp with time zone not null default current_timestamp
);

-- A tela vai buscar as notificações de uma empresa
create index notificacao_empresa_idx on notificacao (empresa_id);
