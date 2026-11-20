package br.com.namtech.atalaia.seguranca;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Trava quem tenta adivinhar senha ou descobrir quais e-mails têm conta (achado do pentest, issue #144).
//Cada envio de /entrar e de /cadastro gasta uma vaga do endereço ANTES de seguir; entrar com a senha certa
//devolve a vaga (successHandler da ConfiguracaoDeSeguranca). Quem é de verdade cria a conta uma vez só.
//Não é @Component: a ConfiguracaoDeSeguranca põe este filtro dentro do Spring Security, logo antes de conferir a senha.
//Como @Component ele rodaria duas vezes, e antes do firewall do Spring
public class TravaDeTentativas extends OncePerRequestFilter {

    //O mesmo comparador de rota que o Spring Security usa no login: ele decodifica o endereço,
    //então /%65ntrar (o "e" escrito em código) também cai aqui (achado da revisão do Hefesto)
    private static final RequestMatcher ENTRAR = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/entrar");
    private static final RequestMatcher CADASTRO = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/cadastro");
    private static final RequestMatcher FORMULARIOS = new OrRequestMatcher(ENTRAR, CADASTRO);

    private final LimiteDeTentativas limite;

    public TravaDeTentativas(LimiteDeTentativas limite) {
        this.limite = limite;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !FORMULARIOS.matches(pedido);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain proximo)
            throws ServletException, IOException {
        //No ar, atrás do proxy da hospedagem, este é o endereço real de quem pediu (ver server.forward-headers-strategy).
        //Travado: nem chega a conferir a senha. Se conferisse, o ataque saberia quando acertou
        if (!limite.reservar(pedido.getRemoteAddr())) {
            String tela = ENTRAR.matches(pedido) ? "/entrar" : "/cadastro";
            resposta.sendRedirect(pedido.getContextPath() + tela + "?travado");
            return;
        }
        proximo.doFilter(pedido, resposta);
    }
}
