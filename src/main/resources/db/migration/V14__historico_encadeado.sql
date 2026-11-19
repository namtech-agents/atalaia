-- V14: a trilha encadeada (US27). Cada evento novo guarda uma assinatura (SHA-256, 64 letras) feita com os dados
-- dele e com a assinatura do evento anterior do mesmo caso. Mudar ou apagar um evento depois quebra a corrente
-- dali em diante, e a conferência (Trilha.conferir) aponta onde.
-- Os eventos de antes desta migração ficam sem assinatura: a trilha de cada caso começa no primeiro evento assinado
alter table evento_do_caso add column assinatura varchar(64);
