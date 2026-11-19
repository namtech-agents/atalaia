//O service worker do Atalaia instalado no celular. Ele faz UMA coisa: sem internet, mostra a tela "Sem conexão"
//(offline.html) em vez do erro do navegador.
//De propósito, NÃO guarda nenhuma página do escritório no aparelho: as telas trazem dados de clientes, e um celular
//perdido não pode abrir a lista de casos sem senha. Só ficam guardados a tela sem conexão e o que ela usa.

//Mudou um arquivo da lista? Troque o número: o celular joga fora a versão velha
const CAIXA = "atalaia-v1";
const GUARDADOS = ["/offline.html", "/css/atalaia.css", "/fontes/archivo-variavel.woff2", "/logo/atalaia-simbolo.svg"];

//Instalação: guarda a tela sem conexão e já assume (skipWaiting), sem esperar o app fechar
self.addEventListener("install", (evento) => {
  evento.waitUntil(caches.open(CAIXA).then((caixa) => caixa.addAll(GUARDADOS)).then(() => self.skipWaiting()));
});

//Ativação: apaga as caixas de versões antigas e passa a cuidar das abas já abertas
self.addEventListener("activate", (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nomes) => Promise.all(nomes.filter((nome) => nome !== CAIXA).map((nome) => caches.delete(nome))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (evento) => {
  const pedido = evento.request;
  if (pedido.method !== "GET") {
    return;
  }
  //Abrir uma tela: sempre pela internet. Sem internet, a tela "Sem conexão"
  if (pedido.mode === "navigate") {
    evento.respondWith(fetch(pedido).catch(() => caches.match("/offline.html")));
    return;
  }
  //O que a tela sem conexão usa: pela internet primeiro (o CSS novo vale na hora), e da caixa só sem internet
  if (GUARDADOS.includes(new URL(pedido.url).pathname)) {
    evento.respondWith(fetch(pedido).catch(() => caches.match(pedido)));
  }
  //O resto (PDF, imagens, telas de dados) segue direto para a internet, sem passar pela caixa
});
