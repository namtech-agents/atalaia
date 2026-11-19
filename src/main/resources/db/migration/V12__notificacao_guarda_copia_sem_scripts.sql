-- V12: quando o PDF enviado traz scripts (JavaScript, abrir programa...), o Atalaia guarda também uma
-- cópia sem eles, e é essa cópia que a tela mostra. O original continua na coluna arquivo, do jeito que chegou.
-- Fica vazia (null) no caso comum, PDF sem script: a tela mostra o próprio original.
alter table notificacao add column arquivo_sem_scripts bytea;
