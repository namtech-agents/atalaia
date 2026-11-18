-- Empresas INVENTADAS para a prévia (o repositório é público: nada de empresa de verdade).
-- O dígito verificador dos CNPJs está errado de propósito, para nenhum deles existir.
-- Arquivo "R__" é repetível: o Flyway roda de novo quando o conteúdo muda, por isso cada linha só entra se faltar.
insert into empresa (cnpj, nome)
select '99999001000100', 'Clínica Vida Mansa (exemplo)'
where not exists (select 1 from empresa where cnpj = '99999001000100');

insert into empresa (cnpj, nome)
select '99999002000100', 'Padaria Pão da Praça (exemplo)'
where not exists (select 1 from empresa where cnpj = '99999002000100');

insert into empresa (cnpj, nome)
select '99999003000100', 'Oficina Roda Viva (exemplo)'
where not exists (select 1 from empresa where cnpj = '99999003000100');
