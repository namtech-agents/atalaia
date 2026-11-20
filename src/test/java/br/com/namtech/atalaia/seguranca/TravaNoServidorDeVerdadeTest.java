package br.com.namtech.atalaia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

//A trava num Tomcat de verdade, numa porta qualquer. O MockMvc não pega o que estes testes conferem:
//endereço escrito em código, pedidos ao mesmo tempo e os cabeçalhos do proxy (achados da revisão do Hefesto).
//O proxy fica ligado (native) e o teste fala de 127.0.0.1, que é o proxy confiável padrão: cada teste
//manda um X-Forwarded-For próprio, como se fosse uma pessoa diferente atrás do proxy.
//Nenhum teste cria conta: o banco H2 é o mesmo dos outros testes e aqui não há transação para desfazer
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = { "atalaia.tentativas.maximo=3", "server.forward-headers-strategy=native" })
class TravaNoServidorDeVerdadeTest {

    @LocalServerPort
    private int porta;

    //Não segue redirecionamento: o teste quer ver para onde o servidor mandou
    private final HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    //O que a tela de entrar entrega para um formulário valer: o cookie da sessão e o código do CSRF
    private record Formulario(String cookie, String csrf) {
    }

    private Formulario abrirTela(String pessoa) throws Exception {
        HttpResponse<String> tela = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/entrar"))
                .header("X-Forwarded-For", pessoa).build(), BodyHandlers.ofString());
        String cookie = tela.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        Matcher codigo = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(tela.body());
        assertThat(codigo.find()).isTrue();
        return new Formulario(cookie, codigo.group(1));
    }

    private HttpResponse<String> enviar(String rota, String pessoa, Formulario formulario, String campos)
            throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + rota))
                .header("X-Forwarded-For", pessoa)
                .header("Cookie", formulario.cookie())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(campos + "&_csrf=" + formulario.csrf())).build(), BodyHandlers.ofString());
    }

    private static String destino(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Location").orElse("");
    }

    private static final String SENHA_ERRADA = "email=ninguem%40escritorio.test&senha=errada";

    @Test
    void enderecoEscritoEmCodigoTambemGastaTentativa() throws Exception {
        //Arrumar: três senhas erradas em /%65ntrar ("e" escrito em código), que o Spring entende como /entrar
        Formulario formulario = abrirTela("203.0.113.10");
        for (int vez = 0; vez < 3; vez++) {
            assertThat(destino(enviar("/%65ntrar", "203.0.113.10", formulario, SENHA_ERRADA))).endsWith("/entrar?erro");
        }

        //Agir e conferir: a quarta, escrita do jeito normal, já está travada
        assertThat(destino(enviar("/entrar", "203.0.113.10", formulario, SENHA_ERRADA))).endsWith("/entrar?travado");
    }

    @Test
    void cadastroEscritoEmCodigoTambemGastaTentativa() throws Exception {
        //Arrumar: três cadastros recusados (senhas diferentes) em /%63adastro ("c" escrito em código)
        Formulario formulario = abrirTela("203.0.113.11");
        String recusado = "escritorio=Teste&email=x%40escritorio.test&senha=uma+frase+comprida&senhaRepetida=outra+frase";
        for (int vez = 0; vez < 3; vez++) {
            assertThat(enviar("/%63adastro", "203.0.113.11", formulario, recusado).statusCode()).isEqualTo(400);
        }

        //Agir e conferir: o quarto está travado
        assertThat(destino(enviar("/cadastro", "203.0.113.11", formulario, recusado))).endsWith("/cadastro?travado");
    }

    @Test
    void rajadaAoMesmoTempoNaoPassaDoMaximo() throws Exception {
        //Arrumar: 20 senhas erradas prontas para sair juntas, do mesmo endereço
        Formulario formulario = abrirTela("203.0.113.12");
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> respostas = new ArrayList<>();
        try (ExecutorService corredores = Executors.newFixedThreadPool(20)) {
            for (int vez = 0; vez < 20; vez++) {
                respostas.add(corredores.submit(() -> {
                    largada.await();
                    return destino(enviar("/entrar", "203.0.113.12", formulario, SENHA_ERRADA));
                }));
            }

            //Agir: todas saem no mesmo instante
            largada.countDown();

            //Conferir: só 3 chegaram a conferir a senha; as outras 17 foram travadas
            int conferidas = 0;
            for (Future<String> resposta : respostas) {
                if (resposta.get().endsWith("/entrar?erro")) {
                    conferidas++;
                }
            }
            assertThat(conferidas).isEqualTo(3);
        }
    }

    @Test
    void atrasDoProxyCadaPessoaTemAPropriaConta() throws Exception {
        //Arrumar: uma pessoa atrás do proxy gasta as três tentativas
        Formulario formulario = abrirTela("203.0.113.13");
        for (int vez = 0; vez < 3; vez++) {
            enviar("/entrar", "203.0.113.13", formulario, SENHA_ERRADA);
        }

        //Conferir: outra pessoa, pelo mesmo proxy, continua conferindo a senha normalmente
        Formulario outro = abrirTela("203.0.113.14");
        assertThat(destino(enviar("/entrar", "203.0.113.14", outro, SENHA_ERRADA))).endsWith("/entrar?erro");
    }

    @Test
    void httpsDoProxyLigaOHsts() throws Exception {
        //Agir: o proxy avisa que a pessoa chegou por HTTPS
        HttpResponse<String> porHttps = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/entrar"))
                .header("X-Forwarded-Proto", "https").build(), BodyHandlers.ofString());
        HttpResponse<String> porHttp = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/entrar"))
                .build(), BodyHandlers.ofString());

        //Conferir: por HTTPS o navegador guarda por um ano que o site é só HTTPS; por HTTP (o Mac) não sai,
        //senão o navegador recusaria o localhost depois
        assertThat(porHttps.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
                valor -> assertThat(valor).contains("max-age=31536000"));
        assertThat(porHttp.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
