// ATALAIA · o menu lateral
// O menu abre, fecha e fixa sem este arquivo: quem faz isso é o CSS, lendo a caixa de marcar #menu-aberto
// (fragmentos.html). Aqui fica só o que o CSS não consegue:
//   1. lembrar, de uma tela para a outra, se a pessoa deixou o menu fixado (só no computador);
//   2. no celular, fechar a gaveta com a tecla Esc e deixar o resto da tela parado enquanto ela está aberta.
// Não é chamado dentro da página (a CSP proíbe script embutido): entra pelo <head>, com defer.

const chave = document.getElementById('menu-aberto');

// Tela sem menu (ninguém entrou): não há o que fazer
if (chave) {
  const GUARDADO = 'atalaia-menu-fixo';
  // A mesma condição que o atalaia.css usa para o menu lateral: janela larga e mouse. Fora dela, é a gaveta
  const computador = matchMedia('(min-width: 861px) and (hover: hover) and (pointer: fine)');
  // O que fica atrás da gaveta: a barra de cima e o conteúdo
  const atras = document.querySelectorAll('.pagina > .topo, .pagina > main');

  // Em janela privada o navegador pode recusar o localStorage: o menu só não lembra
  const lembrar = () => { try { return localStorage.getItem(GUARDADO) === '1'; } catch (e) { return false; } };
  const guardar = fixo => { try { localStorage.setItem(GUARDADO, fixo ? '1' : '0'); } catch (e) { /* segue sem guardar */ } };

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
