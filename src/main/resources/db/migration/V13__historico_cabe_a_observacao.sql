-- V13: reabrir um caso apaga o desfecho e a observação da notificação (o caso volta a ficar aberto).
-- Para nada do que foi registrado se perder, a frase do histórico leva o desfecho anterior e a observação
-- inteira (até 500 letras). Com a frase da ação na frente, 300 não cabiam: a coluna passa a aceitar 600.
alter table evento_do_caso alter column acao type varchar(600);
