package br.com.namtech.atalaia.seguranca;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Filtro: passa por todo pedido antes do controller. Este acrescenta, em toda resposta, os cabeçalhos
//que dizem ao navegador o que a pagina pode e o que nao pode fazer
@Component
public class CabecalhosDeSeguranca extends OncePerRequestFilter {

    //CSP (Content-Security-Policy): a lista do que a pagina pode carregar. 'self' = só do proprio Atalaia.
    //Se um dia um texto malicioso virar HTML na tela, o navegador nao roda script nem busca nada de fora.
    //frame-src: a tela da notificação mostra o PDF numa moldura do proprio site.
    //frame-ancestors: nenhum outro site pode mostrar o Atalaia dentro de uma moldura (golpe do clique escondido).
    //form-action e base-uri: formulario e endereços relativos nao podem ser desviados para fora
    static final String CSP = "default-src 'self'; frame-src 'self'; frame-ancestors 'self'; "
            + "form-action 'self'; base-uri 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain proximo)
            throws ServletException, IOException {
        //A CSP é para pagina HTML. No PDF original ela nao protege nada e poderia atrapalhar
        //o leitor de PDF do navegador, entao essa rota fica só com os outros cabeçalhos
        if (!pedido.getRequestURI().endsWith("/pdf")) {
            resposta.setHeader("Content-Security-Policy", CSP);
        }
        //O mesmo que o frame-ancestors, para navegador antigo
        resposta.setHeader("X-Frame-Options", "SAMEORIGIN");
        //O navegador confia no tipo que o servidor diz, sem tentar adivinhar pelo conteudo
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        //Ao sair para outro site por um link, o navegador nao conta de que pagina veio
        resposta.setHeader("Referrer-Policy", "no-referrer");
        //O Atalaia nao usa camera, microfone nem localização: fica proibido de vez
        resposta.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        proximo.doFilter(pedido, resposta);
    }
}
