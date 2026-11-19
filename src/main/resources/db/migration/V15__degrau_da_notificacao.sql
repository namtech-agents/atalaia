-- US28: o degrau (gravidade) da notificação. Vazio nos casos antigos e quando a IA não sugeriu: a tela pede para escolher
alter table notificacao add column degrau varchar(30);
-- Quem corrigiu o degrau por último. Vazio enquanto o degrau é o sugerido pela IA
alter table notificacao add column degrau_por varchar(255);
