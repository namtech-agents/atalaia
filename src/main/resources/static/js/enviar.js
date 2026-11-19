// ATALAIA · o botão da tela de envio (enviar.html)
// A leitura do PDF e a análise da IA levam alguns segundos. Sem retorno na tela, a pessoa clica de novo, e cada
// clique cria um caso (achado do pentest, issue #144). Ao enviar, o botão desliga e diz o que está acontecendo.
// O servidor também limita os envios por escritório; isto aqui é para a pessoa, não para quem ataca.

const botao = document.getElementById('botao-enviar');

if (botao) {
  const textoOriginal = botao.textContent;

  // "submit" só acontece se o navegador aceitou o formulário (empresa e arquivo escolhidos)
  botao.form.addEventListener('submit', () => {
    botao.disabled = true;
    botao.textContent = 'Lendo o PDF…';
    // aria-busy: o leitor de tela sabe que o formulário está trabalhando
    botao.form.setAttribute('aria-busy', 'true');
  });

  // Voltar pelo navegador pode trazer a página guardada com o botão ainda desligado: liga de novo
  window.addEventListener('pageshow', evento => {
    if (!evento.persisted) return;   // primeira carga: o botão já está ligado
    botao.disabled = false;
    botao.textContent = textoOriginal;
    botao.form.removeAttribute('aria-busy');
  });
}
