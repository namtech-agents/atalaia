// ATALAIA · tela do caso (#214)
// Só o que o HTML não faz sozinho: copiar o link do aviso com um clique.
// O botão nasce escondido (hidden) e só aparece se este arquivo rodar: sem ele, o campo do link continua lá para
// copiar à mão. Entra no fim da página, com defer (a CSP proíbe script dentro da página).

const botao = document.getElementById('copiar-link');
const campo = document.getElementById('link');
const recado = document.getElementById('copiado');

if (botao && campo && recado) {
  botao.hidden = false;
  let apagar;
  botao.addEventListener('click', async () => {
    try {
      // A área de transferência do navegador. Só funciona em HTTPS (no ar) ou no localhost
      await navigator.clipboard.writeText(campo.value);
      recado.textContent = 'Link copiado.';
    } catch (e) {
      // Navegador que recusa: deixa o link selecionado, e a pessoa aperta Cmd+C (ou Ctrl+C)
      campo.focus();
      campo.select();
      recado.textContent = 'Link selecionado: aperte Cmd+C ou Ctrl+C para copiar.';
    }
    // O recado some sozinho depois de alguns segundos (o leitor de tela já leu, pelo role="status")
    clearTimeout(apagar);
    apagar = setTimeout(() => { recado.textContent = ''; }, 4000);
  });
}
