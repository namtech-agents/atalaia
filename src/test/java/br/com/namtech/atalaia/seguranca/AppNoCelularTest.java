package br.com.namtech.atalaia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                .andExpect(content().string(containsString("name=\"theme-color\"")))
                //Sem esta linha o service worker nunca se registra, e a tela sem conexão some sem aviso
                .andExpect(content().string(containsString("src=\"/js/app.js\"")));
    }

    //Nada de dado na tela sem conexão
    @Test
    void telaSemConexaoNaoTemDado() throws Exception {
        assertThat(texto("/offline.html")).contains("Sem conexão");
    }

    //A promessa da #199: celular perdido sem a lista de casos. O service worker escreve na caixa (Cache API) em um
    //lugar só, a instalação com a lista fixa, e a lista são os 4 arquivos da tela sem conexão. Uma escrita a mais
    //(guardar cada tela aberta, por exemplo) derruba este teste, chame a variável como quiser
    @Test
    void serviceWorkerSoGuardaATelaSemConexao() throws Exception {
        String sw = texto("/sw.js");
        assertThat(achados(Pattern.compile("\\.(?:put|add|addAll)\\([^)]*\\)"), sw, 0)).containsExactly(".addAll(GUARDADOS)");
        assertThat(guardados(sw)).containsExactly("/offline.html", "/css/atalaia.css", "/fontes/archivo-variavel.woff2",
                "/logo/atalaia-simbolo.svg");
    }

    //Se um arquivo da lista sumir (fonte renomeada), o addAll falha, o service worker não instala e a tela sem
    //conexão some sem ninguém ver. Cada um precisa abrir, e sem sessão (o celular busca antes do login)
    @Test
    void cadaArquivoGuardadoAbreSemSessao() throws Exception {
        for (String endereco : guardados(texto("/sw.js"))) {
            navegador.perform(get(endereco)).andExpect(status().isOk());
        }
    }

    //Os endereços entre aspas da linha "const GUARDADOS = [...]" do sw.js
    private List<String> guardados(String sw) {
        Matcher lista = Pattern.compile("const GUARDADOS = \\[(.*?)\\];").matcher(sw);
        assertThat(lista.find()).isTrue();
        return achados(Pattern.compile("\"([^\"]+)\""), lista.group(1), 1);
    }

    private List<String> achados(Pattern padrao, String onde, int grupo) {
        return padrao.matcher(onde).results().map(achado -> achado.group(grupo)).toList();
    }

    //Arquivo estático sai sem charset no cabeçalho (o navegador usa o <meta charset>): lido como UTF-8
    private String texto(String endereco) throws Exception {
        return navegador.perform(get(endereco)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
