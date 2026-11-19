package br.com.namtech.atalaia.seguranca;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Trava quem tenta adivinhar senha ou descobrir quais e-mails têm conta (achado do pentest, issue #144).
//O que conta: senha errada em /entrar e todo envio de /cadastro (quem é de verdade cria a conta uma vez só).
//Entrar certo não conta: o escritório inteiro pode entrar pelo mesmo endereço de manhã.
//Order: logo depois dos cabeçalhos e antes do Spring Security, que é quem confere a senha
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TravaDeTentativas extends OncePerRequestFilter {

    private final LimiteDeTentativas limite;

    public TravaDeTentativas(LimiteDeTentativas limite) {
        this.limite = limite;
    }

    //A rota sem o prefixo do site (se um dia o Atalaia morar em /atalaia/..., continua "/entrar")
    private static String rota(HttpServletRequest pedido) {
        return pedido.getRequestURI().substring(pedido.getContextPath().length());
    }

    //O filtro só olha os dois formulários; o resto passa direto
    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        String rota = rota(pedido);
        return !"POST".equals(pedido.getMethod()) || !("/entrar".equals(rota) || "/cadastro".equals(rota));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain proximo)
            throws ServletException, IOException {
        String rota = rota(pedido);
        //No ar, atrás do proxy da hospedagem, este é o endereço real de quem pediu (ver server.forward-headers-strategy)
        String endereco = pedido.getRemoteAddr();

        //Travado: nem chega a conferir a senha. Se conferisse, o ataque saberia quando acertou
        if (limite.travado(endereco)) {
            resposta.sendRedirect(pedido.getContextPath() + rota + "?travado");
            return;
        }

        proximo.doFilter(pedido, resposta);

        //Senha errada: o Spring Security mandou para /entrar?erro (failureUrl da ConfiguracaoDeSeguranca)
        String destino = resposta.getHeader("Location");
        boolean senhaErrada = "/entrar".equals(rota) && destino != null && destino.endsWith("/entrar?erro");
        if (senhaErrada || "/cadastro".equals(rota)) {
            limite.contar(endereco);
        }
    }
}
