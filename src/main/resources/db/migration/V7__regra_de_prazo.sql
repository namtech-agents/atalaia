-- V7: regras de prazo. O Atalaia não traz regra pronta: cada escritório cadastra as que ele mesmo leu na norma,
-- com o artigo e a data da conferência (RNF09). A Calculadora só conta prazo com uma regra daqui

create table regra_de_prazo (
    id                  bigint generated always as identity primary key,
    escritorio_id       bigint not null references escritorio (id),
    nome                varchar(120) not null,
    dias                integer not null,
    -- DIAS_CORRIDOS ou DIAS_UTEIS (TipoDeContagem no Java)
    contagem            varchar(20) not null,
    -- Prazo da Justiça para de 20/12 a 20/01
    suspende_no_recesso boolean not null,
    -- De onde a regra saiu: artigo e norma. Sem isso a regra não entra
    artigo              varchar(200) not null,
    -- O dia em que alguém do escritório conferiu a regra na norma
    conferida_em        date not null,
    criada_em           timestamp with time zone not null default current_timestamp,
    constraint regra_de_prazo_dias_validos check (dias between 1 and 365),
    constraint regra_de_prazo_contagem_valida check (contagem in ('DIAS_CORRIDOS', 'DIAS_UTEIS')),
    -- Dentro de um escritório o nome não repete: é por ele que a pessoa escolhe a regra na tela
    constraint regra_de_prazo_nome_unico_no_escritorio unique (escritorio_id, nome)
);
