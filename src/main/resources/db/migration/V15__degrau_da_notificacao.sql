-- US28: o degrau (gravidade) da notificação. Vazio nos casos antigos e quando a IA não sugeriu: a tela pede para escolher
alter table notificacao add column degrau varchar(30);
