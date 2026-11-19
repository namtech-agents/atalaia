package br.com.namtech.atalaia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

//O Atalaia instalado no celular (PWA, issue #199): o que o celular busca antes de alguém entrar abre sem sessão,
//e as telas apontam para o manifesto
@SpringBootTest
@AutoConfigureMockMvc
class AppNoCelularTest {

    @Autowired
    private MockMvc navegador;

    //Sem sessão: o celular busca o manifesto e o ícone para montar o atalho, e o service worker antes do login
    @ParameterizedTest
    @ValueSource(strings = { "/manifest.json", "/sw.js", "/offline.html", "/js/app.js", "/icones/icone-192.png",
            "/icones/icone-512.png", "/icones/icone-maskable-512.png", "/icones/apple-touch-icon.png" })
    void oQueOAppPrecisaAbreSemSessao(String endereco) throws Exception {
        navegador.perform(get(endereco)).andExpect(status().isOk());
    }

    @Test
    void manifestoDizNomeTelaCheiaEIcones() throws Exception {
        navegador.perform(get("/manifest.json"))
                .andExpect(jsonPath("$.name").value("Atalaia"))
                .andExpect(jsonPath("$.display").value("standalone"))
                .andExpect(jsonPath("$.start_url").value("/"))
                .andExpect(jsonPath("$.icons.length()").value(3));
    }

    @Test
    void telaAbertaApontaParaOManifestoEOIconeDoIphone() throws Exception {
        navegador.perform(get("/entrar"))
                .andExpect(content().string(containsString("rel=\"manifest\" href=\"/manifest.json\"")))
                .andExpect(content().string(containsString("rel=\"apple-touch-icon\" href=\"/icones/apple-touch-icon.png\"")))
                .andExpect(content().string(containsString("name=\"theme-color\"")));
    }

    //Nada de dado na tela sem conexão, e o service worker não guarda tela do escritório (só a lista fixa)
    @Test
    void telaSemConexaoEServiceWorkerNaoGuardamDados() throws Exception {
        //A página estática sai sem o charset no cabeçalho (o navegador usa o <meta charset> dela): lida como UTF-8
        assertThat(navegador.perform(get("/offline.html")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).contains("Sem conexão");
        navegador.perform(get("/sw.js"))
                .andExpect(content().string(containsString("const GUARDADOS = [\"/offline.html\", \"/css/atalaia.css\"")))
                .andExpect(content().string(not(containsString("cache.put"))));
    }
}
