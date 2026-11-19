package br.com.namtech.atalaia;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.conta.UsuarioLogado;

//Apoio dos testes: monta o "navegador" de mentira (MockMvc) do jeito que cada teste precisa
public final class NavegadorDeTeste {

    private NavegadorDeTeste() {
    }

    //Com a sessão aberta: todo pedido sai como se a pessoa ja tivesse entrado, e leva o codigo que protege
    //os formularios (CSRF), como a pagina de verdade leva. Os testes das telas nao precisam repetir isso
    public static MockMvc logado(WebApplicationContext aplicacao, UsuarioLogado quem) {
        return MockMvcBuilders.webAppContextSetup(aplicacao).apply(springSecurity())
                .defaultRequest(get("/").with(user(quem)).with(csrf()))
                .build();
    }

    //Uma pessoa inventada do escritório dado. O e-mail nao importa para as telas: o que separa os dados é o escritório
    public static UsuarioLogado pessoaDo(Long escritorioId) {
        return new UsuarioLogado("pessoa@escritorio.test", "", escritorioId);
    }
}
