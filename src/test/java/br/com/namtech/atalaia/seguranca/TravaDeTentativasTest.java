package br.com.namtech.atalaia.seguranca;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

//A trava ligada no servidor de verdade. Aqui o máximo é 3 (nos outros testes ele é alto, ver
//src/test/resources/application.properties), e cada teste usa um endereço próprio para um não gastar a conta do outro
@SpringBootTest(properties = "atalaia.tentativas.maximo=3")
@AutoConfigureMockMvc
@Transactional
class TravaDeTentativasTest {

    @Autowired
    private MockMvc navegador;

    //Faz o pedido chegar como se viesse deste endereço
    private static RequestPostProcessor vindoDe(String endereco) {
        return pedido -> {
            pedido.setRemoteAddr(endereco);
            return pedido;
        };
    }

    private static MockHttpServletRequestBuilder entrada(String endereco, String senha) {
        return post("/entrar").with(csrf()).with(vindoDe(endereco))
                .param("email", "ninguem@escritorio.test").param("senha", senha);
    }

    private static MockHttpServletRequestBuilder cadastro(String endereco, String email) {
        return post("/cadastro").with(csrf()).with(vindoDe(endereco))
                .param("escritorio", "Escritório de Teste").param("email", email)
                .param("senha", "uma frase comprida").param("senhaRepetida", "uma frase comprida");
    }

    @Test
    void senhaErradaDemaisTravaAEntrada() throws Exception {
        //Arrumar: três senhas erradas do mesmo endereço
        for (int vez = 0; vez < 3; vez++) {
            navegador.perform(entrada("203.0.113.1", "errada" + vez)).andExpect(redirectedUrl("/entrar?erro"));
        }

        //Agir e conferir: a quarta nem chega a conferir a senha; volta para a tela com o aviso de espera
        navegador.perform(entrada("203.0.113.1", "outra"))
                .andExpect(redirectedUrl("/entrar?travado"))
                .andExpect(unauthenticated());
        navegador.perform(get("/entrar").param("travado", ""))
                .andExpect(content().string(containsString("Muitas tentativas seguidas")));
    }

    @Test
    void travadoNaoEntraNemComASenhaCerta() throws Exception {
        //Arrumar: a conta existe (criada de outro endereço) e este endereço erra três vezes
        navegador.perform(cadastro("203.0.113.2", "certa@escritorio.test")).andExpect(redirectedUrl("/"));
        for (int vez = 0; vez < 3; vez++) {
            navegador.perform(post("/entrar").with(csrf()).with(vindoDe("203.0.113.3"))
                    .param("email", "certa@escritorio.test").param("senha", "errada" + vez));
        }

        //Agir e conferir: com a senha certa, continua travado (senão o ataque saberia quando acertou)
        navegador.perform(post("/entrar").with(csrf()).with(vindoDe("203.0.113.3"))
                .param("email", "certa@escritorio.test").param("senha", "uma frase comprida"))
                .andExpect(redirectedUrl("/entrar?travado"))
                .andExpect(unauthenticated());

        //Conferir: de outro endereço, a pessoa entra normalmente
        navegador.perform(post("/entrar").with(csrf()).with(vindoDe("203.0.113.4"))
                .param("email", "certa@escritorio.test").param("senha", "uma frase comprida"))
                .andExpect(authenticated());
    }

    @Test
    void entrarCertoNaoGastaTentativa() throws Exception {
        //Arrumar: a conta existe
        navegador.perform(cadastro("203.0.113.5", "colega@escritorio.test")).andExpect(redirectedUrl("/"));

        //Agir: o escritório inteiro entra pelo mesmo endereço de manhã, mais vezes que o máximo
        for (int vez = 0; vez < 4; vez++) {
            navegador.perform(post("/entrar").with(csrf()).with(vindoDe("203.0.113.6"))
                    .param("email", "colega@escritorio.test").param("senha", "uma frase comprida"))
                    //Conferir: todas entram
                    .andExpect(authenticated());
        }
    }

    @Test
    void cadastrosDemaisDoMesmoEnderecoTravam() throws Exception {
        //Arrumar: três cadastros (um certo e dois com e-mail repetido, que é como se descobre quem tem conta)
        navegador.perform(cadastro("203.0.113.7", "nova@escritorio.test")).andExpect(redirectedUrl("/"));
        navegador.perform(cadastro("203.0.113.7", "nova@escritorio.test")).andExpect(status().isBadRequest());
        navegador.perform(cadastro("203.0.113.7", "nova@escritorio.test")).andExpect(status().isBadRequest());

        //Agir e conferir: o quarto volta para a tela de cadastro com o aviso, sem dizer nada sobre o e-mail
        navegador.perform(cadastro("203.0.113.7", "outra@escritorio.test"))
                .andExpect(redirectedUrl("/cadastro?travado"));
        navegador.perform(get("/cadastro").param("travado", ""))
                .andExpect(content().string(containsString("Muitas tentativas seguidas")));
    }

    @Test
    void porHttpsAPaginaPedeAoNavegadorParaNuncaUsarHttp() throws Exception {
        //Agir e conferir: HSTS. Vindo por HTTPS, o navegador guarda por um ano que este site é só HTTPS
        navegador.perform(get("/entrar").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }

    @Test
    void porHttpNaoMandaHsts() throws Exception {
        //Agir e conferir: no Mac (http://localhost) o cabeçalho não sai, senão o navegador recusaria o localhost depois
        navegador.perform(get("/entrar"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
