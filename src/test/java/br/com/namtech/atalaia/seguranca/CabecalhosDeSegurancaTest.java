package br.com.namtech.atalaia.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CabecalhosDeSegurancaTest {

    @Autowired
    private MockMvc navegador;

    @Test
    void paginaSaiComOsCabecalhosDeSeguranca() throws Exception {
        //Agir e conferir: a tela de envio abre e traz cada cabeçalho
        navegador.perform(get("/enviar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void paginaDeErroTambemSaiComOsCabecalhos() throws Exception {
        //Agir e conferir: até o "não encontrado" sai protegido
        navegador.perform(get("/notificacoes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP));
    }
}
