//Liga o service worker (sw.js), que mostra a tela "Sem conexão" quando o app instalado está sem internet.
//Navegador sem suporte segue funcionando igual, só sem essa tela
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js");
}
