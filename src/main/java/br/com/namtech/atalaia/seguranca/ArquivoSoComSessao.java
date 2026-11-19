package br.com.namtech.atalaia.seguranca;

import java.io.IOException;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Envio de arquivo só de quem entrou (achado do pentest, issue #144). O filtro do CSRF procura o código escondido
//do formulário DENTRO do corpo do pedido: num envio de arquivo, isso faz o servidor ler o arquivo inteiro (até 16 MB)
//antes de saber que a pessoa nem entrou. Este filtro roda antes do CSRF e manda quem não entrou para a tela de
//entrar sem ler nada. O Tomcat ainda descarta até 2 MB do que sobrou e fecha a conexão.
//Não é @Component pelo mesmo motivo da TravaDeTentativas: quem põe na fila é a ConfiguracaoDeSeguranca
public class ArquivoSoComSessao extends OncePerRequestFilter {

    //Sabe distinguir quem entrou de verdade de quem só está navegando (o "anônimo" do Spring Security)
    private final AuthenticationTrustResolver quemE = new AuthenticationTrustResolverImpl();

    //Todo tipo que começa com "multipart", escrito de qualquer jeito. Comparar com "multipart/form-data" inteiro
    //deixava passar "multipart/ form-data" (com espaço) e "multipart/mixed", que o servidor também lê
    //(achado da revisão do Hefesto). Os outros formulários são pequenos e o Tomcat corta em 2 MB
    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !StringUtils.startsWithIgnoreCase(pedido.getContentType(), "multipart");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain proximo)
            throws ServletException, IOException {
        //Quem entrou vem da sessão (cookie), sem olhar o corpo do pedido. Sem sessão, quase sempre é alguém que
        //deixou a tela aberta até a sessão vencer: ?expirou faz a tela de entrar explicar que o envio não foi
        if (!quemE.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
            resposta.sendRedirect(pedido.getContextPath() + "/entrar?expirou");
            return;
        }
        proximo.doFilter(pedido, resposta);
    }
}
