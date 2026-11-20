// ATALAIA · o menu lateral
// O menu abre, fecha e fixa sem este arquivo: quem faz isso é o CSS, lendo a caixa de marcar #menu-aberto
// (fragmentos.html). Aqui fica só o que o CSS não consegue:
//   1. lembrar, de uma tela para a outra, se a pessoa deixou o menu fixado (só no computador);
//   2. no celular, fechar a gaveta com a tecla Esc e deixar o resto da tela parado enquanto ela está aberta;
//   3. manter o menu aberto na tela seguinte quando a pessoa clica num item com ele aberto pelo mouse;
//   4. passar os cartões da vitrine no pé do menu.
// Não é chamado dentro da página (a CSP proíbe script embutido): entra pelo <head>, com defer.

const chave = document.getElementById('menu-aberto');

// Tela sem menu (ninguém entrou): não há o que fazer
if (chave) {
  const GUARDADO = 'atalaia-menu-fixo';
  // A mesma condição que o atalaia.css usa para o menu lateral: janela larga e mouse. Fora dela, é a gaveta
  const computador = matchMedia('(min-width: 861px) and (hover: hover) and (pointer: fine)');
  // O que fica atrás da gaveta: o link de pular (o destino dele também fica parado), a barra de cima e o conteúdo
  const atras = document.querySelectorAll('.pagina > .pular, .pagina > .topo, .pagina > main');

  // Em janela privada o navegador pode recusar o localStorage: o menu só não lembra
  const lembrar = () => { try { return localStorage.getItem(GUARDADO) === '1'; } catch (e) { return false; } };
  const guardar = fixo => { try { localStorage.setItem(GUARDADO, fixo ? '1' : '0'); } catch (e) { /* segue sem guardar */ } };

  // 3. Clicou num item com o menu aberto pelo mouse (#202): a tela seguinte nasce com ele aberto, em vez de nascer
  //    fechada e abrir de novo, empurrando o conteúdo de um lado para o outro a cada clique. sessionStorage: vale
  //    só para esta aba e só para o próximo carregamento. Ao tirar o mouse, ele volta ao normal (fecha devagar)
  const PASSAGEM = 'atalaia-menu-passagem';
  const pagina = chave.closest('.pagina');
  const menu = chave.closest('.menu');
  menu.addEventListener('click', clique => {
    // Clique com Cmd, Ctrl, Shift ou Alt abre o link em outra aba ou janela: esta aba não troca de tela
    const outraAba = clique.button !== 0 || clique.metaKey || clique.ctrlKey || clique.shiftKey || clique.altKey;
    if (computador.matches && !chave.checked && !outraAba && clique.target.closest('a.m-item') && menu.matches(':hover')) {
      // Guarda a hora, e não só "sim": se a tela nova não chegar (rede caiu, Esc), o aviso vence sozinho
      try { sessionStorage.setItem(PASSAGEM, String(Date.now())); } catch (e) { /* só não segura o menu aberto */ }
    }
  });
  try {
    const quando = Number(sessionStorage.getItem(PASSAGEM));
    sessionStorage.removeItem(PASSAGEM);
    if (quando && Date.now() - quando < 5000) {
      pagina.classList.add('m-segue');
      // Solta quando o mouse se mexer de verdade e estiver fora do menu. Não dá para usar "mouseleave": durante a
      // troca de tela a transição cobre a página por um instante e o navegador acha que o mouse saiu
      const soltar = () => {
        if (menu.matches(':hover')) return;
        pagina.classList.remove('m-segue');
        document.removeEventListener('mousemove', soltar);
      };
      document.addEventListener('mousemove', soltar);
    }
  } catch (e) { /* sem sessionStorage, o menu só nasce fechado */ }

  // 4. A vitrine (#204): passa sozinha a cada 5 s, mas para com o mouse em cima, com o foco dentro, com a aba
  //    escondida, com o menu recolhido (aí ela nem aparece) e quando a pessoa aperta pausar. Quem pediu menos
  //    movimento já começa com ela parada, e a troca não desliza. Ela não volta para a logo a cada tela: retoma
  //    o cartão em que estava (sessionStorage)
  const vitrine = menu.querySelector('.vitrine');
  if (vitrine) {
    const cartoes = [...vitrine.querySelectorAll('.v-cartao')];
    const pontos = [...vitrine.querySelectorAll('.v-pontos i')];
    const janela = vitrine.querySelector('.v-janela');
    const pausa = vitrine.querySelector('.v-pausa');
    const calmo = matchMedia('(prefers-reduced-motion: reduce)');
    const LUGAR = 'atalaia-vitrine';
    let atual = 0;
    let parada = calmo.matches;
    let olhando = false;
    let andando = false;        // um deslize em curso: outro clique espera ele terminar (senão os cartões pulam)
    let ultimoClique = 0;       // depois de trocar à mão, o relógio espera 5 s inteiros antes de passar sozinho

    // O leitor de tela fala o cartão novo só quando a pessoa está no controle (parada ou com o foco dentro).
    // Passando sozinha, fica quieta: anunciar a cada 5 s atrapalharia quem está lendo outra coisa
    const avisar = () => janela.setAttribute('aria-live', parada || olhando ? 'polite' : 'off');

    // mostra o cartão "para"; lado = 1 desliza para a esquerda (próximo), -1 para a direita (anterior)
    const ir = (para, lado, animar) => {
      para = (((para % cartoes.length) + cartoes.length) % cartoes.length);
      if (para === atual || andando) return;
      const sai = cartoes[atual];
      const entra = cartoes[para];
      sai.classList.remove('atual');
      entra.classList.add('atual');
      pontos[atual].classList.remove('atual');
      pontos[para].classList.add('atual');
      if (animar && !calmo.matches) {
        // os dois deslizam juntos (só transform); quem sai continua à vista até terminar
        const como = { duration: 520, easing: 'cubic-bezier(.32, .72, 0, 1)' };
        andando = true;
        sai.classList.add('saindo');
        sai.animate([{ transform: 'none' }, { transform: `translateX(${-100 * lado}%)` }], como)
          .onfinish = () => { sai.classList.remove('saindo'); andando = false; };
        entra.animate([{ transform: `translateX(${100 * lado}%)` }, { transform: 'none' }], como);
      }
      atual = para;
      try { sessionStorage.setItem(LUGAR, String(atual)); } catch (e) { /* só não lembra o cartão */ }
    };

    // Math.trunc: um valor estranho guardado (ex.: "1.5") vira um cartão que existe
    try { ir(Math.trunc(Number(sessionStorage.getItem(LUGAR))) || 0, 1, false); } catch (e) { /* começa na logo */ }

    // O estado vai num data-parada (o CSS troca o desenho) e o nome do botão diz o que ele FAZ.
    // Sem aria-pressed: junto com o nome que muda, o leitor diria "Continuar, pressionado", que confunde
    const pausar = sim => {
      parada = sim;
      pausa.dataset.parada = String(sim);
      pausa.setAttribute('aria-label', sim ? 'Continuar a vitrine' : 'Pausar a vitrine');
      avisar();
    };
    pausar(parada);

    // checkVisibility percebe também a gaveta fechada (visibility: hidden), além do menu recolhido e da janela
    // baixa (display: none). Navegador antigo sem ele: offsetParent, que só percebe o display: none
    // Olha a janela dos cartões, e não a vitrine inteira: com o menu recolhido a vitrine continua lá (é ela que
    // mostra o cartãozinho da logo), só a janela some
    const aVista = () => (janela.checkVisibility ? janela.checkVisibility({ visibilityProperty: true }) : !!janela.offsetParent);
    setInterval(() => {
      if (!parada && !olhando && !document.hidden && aVista() && Date.now() - ultimoClique >= 5000) ir(atual + 1, 1, true);
    }, 5000);

    const aMao = (para, lado) => { ultimoClique = Date.now(); ir(para, lado, true); };
    pausa.addEventListener('click', () => pausar(!parada));
    vitrine.querySelector('.v-ant').addEventListener('click', () => aMao(atual - 1, -1));
    vitrine.querySelector('.v-prox').addEventListener('click', () => aMao(atual + 1, 1));
    vitrine.addEventListener('mouseenter', () => { olhando = true; });
    vitrine.addEventListener('mouseleave', () => { olhando = vitrine.contains(document.activeElement); });
    vitrine.addEventListener('focusin', () => { olhando = true; avisar(); });
    vitrine.addEventListener('focusout', saida => { olhando = vitrine.contains(saida.relatedTarget) || vitrine.matches(':hover'); avisar(); });
  }

  // inert: com a gaveta aberta, o teclado e o leitor de tela não entram no que está atrás dela
  const parar = () => { atras.forEach(peca => { peca.inert = !computador.matches && chave.checked; }); };

  // Ao chegar numa tela (e ao girar o aparelho ou mudar a largura da janela):
  // no computador o menu volta como a pessoa deixou; no celular a gaveta sempre começa fechada
  const arrumar = () => { chave.checked = computador.matches && lembrar(); parar(); };

  // "change" acontece quando a pessoa marca ou desmarca (pelo botão, pelo véu ou pela barra de espaço)
  chave.addEventListener('change', () => { if (computador.matches) guardar(chave.checked); parar(); });
  computador.addEventListener('change', arrumar);
  // pageshow: também vale para a tela que volta pelo botão "voltar" do navegador
  window.addEventListener('pageshow', arrumar);

  // Esc fecha a gaveta e devolve o foco para a chave, que é quem abre de novo.
  // preventScroll: a página não rola por causa disso (quem tinha rolado continua onde estava)
  document.addEventListener('keydown', tecla => {
    if (tecla.key !== 'Escape' || computador.matches || !chave.checked) return;
    chave.checked = false; parar(); chave.focus({ preventScroll: true });
  });

  arrumar();
}
