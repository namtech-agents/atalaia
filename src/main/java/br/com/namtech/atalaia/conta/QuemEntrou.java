package br.com.namtech.atalaia.conta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

//ControllerAdvice: vale para todos os controllers. Este entrega a toda tela o e-mail de quem esta com a sessão
//aberta ("quemEntrou"), para a barra de cima mostrar o menu e o botão de sair. Sem sessão, fica vazio
@ControllerAdvice
public class QuemEntrou {

    @ModelAttribute("quemEntrou")
    public String quemEntrou(@AuthenticationPrincipal UsuarioLogado usuario) {
        return usuario == null ? null : usuario.email();
    }
}
