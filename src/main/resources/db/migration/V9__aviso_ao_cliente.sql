-- V9: o aviso ao cliente. O escritório escreve o resumo, gera um link e manda ao dono da empresa;
-- ele responde uma pergunta e confirma. Cada linha é um aviso: o que foi mostrado e o registro de ciência

create table aviso_ao_cliente (
    id             bigint generated always as identity primary key,
    notificacao_id bigint not null references notificacao (id),
    -- O segredo do link (/c/...): 32 bytes sorteados, impossível de adivinhar
    token          varchar(64) not null,
    -- O que o cliente viu. Fica copiado aqui: se a notificação mudar depois, o registro continua igual
    resumo         varchar(600) not null,
    -- Os documentos que ele precisa separar, um por linha. Pode ficar vazio
    documentos     varchar(1000),
    prazo          date not null,
    criado_em      timestamp with time zone not null,
    criado_por     varchar(254) not null,
    -- Depois deste momento o link não confirma mais
    vale_ate       timestamp with time zone not null,
    -- Preenchido quando o escritório faz um aviso novo no lugar deste
    cancelado_em   timestamp with time zone,
    -- O registro de ciência: quando o cliente confirmou e a data que ele marcou na pergunta
    confirmado_em  timestamp with time zone,
    resposta       date,
    constraint aviso_ao_cliente_token_unico unique (token),
    -- Ou ainda não confirmou (os dois vazios), ou confirmou respondendo (os dois preenchidos)
    constraint aviso_ao_cliente_confirmado_por_inteiro check (
        (confirmado_em is null and resposta is null) or (confirmado_em is not null and resposta is not null)
    )
);
create index aviso_ao_cliente_notificacao_idx on aviso_ao_cliente (notificacao_id);
