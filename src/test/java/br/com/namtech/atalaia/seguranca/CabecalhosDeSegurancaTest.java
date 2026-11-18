package br.com.namtech.atalaia.seguranca;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.com.namtech.atalaia.NavegadorDeTeste;

@SpringBootTest
@AutoConfigureMockMvc
class CabecalhosDeSegurancaTest {

    @Autowired
    private MockMvc navegador;

    @Test
    void paginaSaiComOsCabecalhosDeSeguranca() throws Exception {
        //Agir e conferir: a tela de entrar (aberta a todos) traz cada cabeçalho
        navegador.perform(get("/entrar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void semSessaoOPedidoVaiParaATelaDeEntrarTambemComOsCabecalhos() throws Exception {
        //Agir e conferir: quem nao entrou é levado para a tela de entrar, e até essa resposta sai protegida
        navegador.perform(get("/enviar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/entrar")))
                .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP));
    }

    @Test
    void paginaDeErroTambemSaiComOsCabecalhos() throws Exception {
        //Agir e conferir: até o "não encontrado" de quem ja entrou sai protegido
        //(a pessoa é de um escritório que nao existe no banco: para ela, nenhuma notificação existe)
        navegador.perform(get("/notificacoes/999999").with(user(NavegadorDeTeste.pessoaDo(0L))))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP))
                //o padrao do Spring Security seria DENY, que esconderia o PDF ao lado do caso
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}
