package br.com.namtech.atalaia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Part;

//Quem não entrou não consegue fazer o Atalaia ler um arquivo enviado (achado do pentest, issue #144).
//O teste passa o pedido pela fila inteira do Spring Security com um pedido que denuncia qualquer leitura do corpo:
//se algum filtro tentar ler (o do CSRF lê para achar o código escondido do formulário), o teste falha.
//O MockMvc comum não serve aqui: ele já entrega o arquivo lido antes de os filtros rodarem
@SpringBootTest
class ArquivoSoComSessaoTest {

    //A fila de filtros do Spring Security, a mesma que fica na frente de todo pedido no ar
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurity;

    //Um envio de arquivo que falha o teste se alguém tentar ler o que veio dentro
    private static final class PedidoQueNaoPodeSerLido extends MockHttpServletRequest {

        PedidoQueNaoPodeSerLido() {
            super("POST", "/enviar");
            setContentType("multipart/form-data; boundary=fronteira");
        }

        private static AssertionError leu() {
            return new AssertionError("O corpo do pedido foi lido antes de conferir a sessão");
        }

        @Override
        public String getParameter(String nome) {
            throw leu();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            throw leu();
        }

        @Override
        public Enumeration<String> getParameterNames() {
            throw leu();
        }

        @Override
        public String[] getParameterValues(String nome) {
            throw leu();
        }

        @Override
        public Collection<Part> getParts() {
            throw leu();
        }

        @Override
        public ServletInputStream getInputStream() {
            throw leu();
        }

        @Override
        public BufferedReader getReader() {
            throw leu();
        }
    }

    @Test
    void envioDeArquivoSemSessaoVaiParaATelaDeEntrarSemSerLido() throws Exception {
        //Arrumar: um envio de arquivo de quem não entrou
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        //Agir: passa pela fila do Spring Security. Se chegar ao fim dela, algo deixou passar
        springSecurity.doFilter(new PedidoQueNaoPodeSerLido(), resposta, (pedido, saida) -> {
            throw new AssertionError("O envio sem sessão passou pelo Spring Security");
        });

        //Conferir: foi mandado para a tela de entrar, sem ninguém ler o arquivo
        assertThat(resposta.getStatus()).isEqualTo(302);
        assertThat(resposta.getRedirectedUrl()).isEqualTo("/entrar");
    }
}
