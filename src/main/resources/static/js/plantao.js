// ATALAIA · o plantão da tela de entrar (a parede azul de fragmentos.html)
// Mostra o dia, a hora, o ponteiro na régua e a situação do expediente, tudo pelo relógio de quem abriu a página.
// Nada aqui é enviado a lugar nenhum e nada é dado inventado.
// Quem chama é o pedaço "parede" do fragmentos.html. A tela de entrar é aberta por quem não tem sessão, por isso
// a pasta /js está na lista de caminhos abertos do ConfiguracaoDeSeguranca.java.

const ABRE = 8, FECHA = 18;   // o expediente; a faixa clara da régua (.expediente, no atalaia.css) usa as mesmas horas

const peca = id => document.getElementById('plantao-' + id);

// "2 h 10 min", "45 min", "3 h"
const falta = minutos => {
  const h = Math.floor(minutos / 60), m = minutos % 60;
  return [h ? h + ' h' : '', m ? m + ' min' : ''].filter(Boolean).join(' ') || 'menos de 1 min';
};

const mostrar = () => {
  const agora = new Date();
  const minutos = agora.getHours() * 60 + agora.getMinutes();
  const fimDeSemana = agora.getDay() === 0 || agora.getDay() === 6;

  peca('dia').textContent = agora.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  peca('hora').textContent = agora.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  // O ponteiro anda da esquerda (0h) para a direita (24h). Mudar o estilo por aqui a CSP permite;
  // o que ela proíbe é o atributo style escrito no HTML
  peca('agora').style.left = (minutos / 1440 * 100) + '%';
  // No fim de semana a faixa do expediente apaga, para não contradizer a frase
  peca('regua').classList.toggle('fds', fimDeSemana);
  peca('situacao').textContent =
    fimDeSemana ? 'Fim de semana, fora do expediente.'
    : minutos < ABRE * 60 ? `O expediente começa às ${ABRE}h. Faltam ${falta(ABRE * 60 - minutos)}.`
    : minutos < FECHA * 60 ? `Expediente até as ${FECHA}h. Faltam ${falta(FECHA * 60 - minutos)}.`
    : 'Expediente encerrado por hoje.';

  // As peças nascem escondidas no HTML: só aparecem depois de preenchidas
  ['dia', 'hora', 'agora', 'situacao'].forEach(id => { peca(id).hidden = false; });
};

// Só nas telas que têm a parede
if (peca('hora')) {
  mostrar();
  setInterval(mostrar, 20000);   // a cada 20 segundos: o minuto vira sem atraso que se note
}
