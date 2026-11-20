// ATALAIA · o menu lateral
// O menu abre, fecha e fixa sem este arquivo: quem faz isso é o CSS, lendo a caixa de marcar #menu-aberto
// (fragmentos.html). Aqui fica só o que o CSS não consegue:
//   1. lembrar, de uma tela para a outra, se a pessoa deixou o menu fixado (só no computador);
//   2. no celular, fechar a gaveta com a tecla Esc e deixar o resto da tela parado enquanto ela está aberta;
//   3. manter o menu aberto na tela seguinte quando a pessoa clica num item com ele aberto pelo mouse.
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
      // troca de tela a fusão cobre a página por um instante e o navegador acha que o mouse saiu
      const soltar = () => {
        if (menu.matches(':hover')) return;
        pagina.classList.remove('m-segue');
        document.removeEventListener('mousemove', soltar);
      };
      document.addEventListener('mousemove', soltar);
    }
  } catch (e) { /* sem sessionStorage, o menu só nasce fechado */ }

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
