package br.com.namtech.atalaia.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;

//Aqui o navegador de mentira começa SEM sessão (AutoConfigureMockMvc): é o que estes testes querem ver.
//csrf(): poe no pedido o codigo escondido que a pagina de verdade leva em todo formulario
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaControllerTest {

    private static final String SENHA = "uma frase comprida";

    @Autowired
    private MockMvc navegador;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private EmpresaRepository empresas;

    private MockHttpServletRequestBuilder cadastro(String email, String senha) {
        return post("/cadastro").with(csrf())
                .param("escritorio", "Escritório de Teste").param("email", email)
                .param("senha", senha).param("senhaRepetida", senha);
    }

    @Test
    void cadastroCriaAContaEAPessoaConsegueEntrar() throws Exception {
        //Agir: cria a conta, com o e-mail digitado com maiuscula e espaço
        MockHttpSession sessao = (MockHttpSession) navegador.perform(cadastro("  Ana@Escritorio.TEST ", SENHA))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession();

        //Conferir: a conta ja nasce com a sessão aberta (a tela inicial abre sem pedir para entrar)
        navegador.perform(get("/").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comece cadastrando a primeira empresa")));

        //Conferir: a conta ficou guardada com o e-mail arrumado e o escritório, e a senha nao esta escrita no banco
        Usuario guardada = usuarios.findByEmail("ana@escritorio.test").orElseThrow();
        assertThat(guardada.getEscritorio().getNome()).isEqualTo("Escritório de Teste");
        assertThat(guardada.getSenha()).startsWith("{bcrypt}").doesNotContain(SENHA);

        //Conferir: o que ela cadastra cai no escritório dela (a sessão de verdade carrega o escritório certo)
        navegador.perform(post("/empresas").session(sessao).with(csrf())
                .param("nome", "Padaria de Teste").param("cnpj", "99999009000100"));
        assertThat(empresas.findAllByEscritorioIdOrderByNome(guardada.getEscritorio().getId())).hasSize(1);

        //Conferir: noutro dia, com o e-mail e a senha, a pessoa entra
        navegador.perform(formLogin("/entrar").userParameter("email").passwordParam("senha")
                .user("ana@escritorio.test").password(SENHA))
                .andExpect(authenticated().withUsername("ana@escritorio.test"));
    }

    @Test
    void senhaErradaNaoEntraEVoltaParaATelaComOAviso() throws Exception {
        //Arrumar: uma conta criada
        navegador.perform(cadastro("ana@escritorio.test", SENHA));

        //Agir e conferir: com a senha errada nao entra, e a tela avisa sem dizer qual dos dois errou
        navegador.perform(formLogin("/entrar").userParameter("email").passwordParam("senha")
                .user("ana@escritorio.test").password("outra senha qualquer"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/entrar?erro"));
        //(o navegador de mentira nao entende "?erro" sem valor no endereço; o de verdade manda o parametro vazio, como aqui)
        navegador.perform(get("/entrar").param("erro", ""))
                .andExpect(content().string(containsString("E-mail ou senha não conferem")));
    }

    @Test
    void cadastroComSenhaCurtaSenhasDiferentesEmailErradoOuEmailRepetidoERecusado() throws Exception {
        //Arrumar: uma conta que ja existe
        navegador.perform(cadastro("ana@escritorio.test", SENHA));

        //Agir e conferir: cada pedido errado volta com a sua mensagem
        navegador.perform(cadastro("bia@escritorio.test", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("pelo menos 8")));
        //(oito espaços tem o tamanho certo, mas nao é senha)
        navegador.perform(cadastro("bia@escritorio.test", "        "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("pelo menos 8")));
        navegador.perform(cadastro("bia@escritorio.test", SENHA).param("senhaRepetida", "outra frase comprida"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("As duas senhas não são iguais")));
        navegador.perform(cadastro("bia-sem-arroba", SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escreva um e-mail válido")));
        navegador.perform(cadastro("ANA@escritorio.test", SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Já existe uma conta com este e-mail")));

        //Conferir: só a primeira conta existe
        assertThat(usuarios.existsByEmail("bia@escritorio.test")).isFalse();
        assertThat(usuarios.findAll()).filteredOn(usuario -> usuario.getEmail().equals("ana@escritorio.test")).hasSize(1);
    }

    @Test
    void formularioSemOCodigoDeProtecaoERecusado() throws Exception {
        //Agir e conferir: um pedido de cadastro que nao saiu da nossa pagina (sem o codigo CSRF) é barrado
        navegador.perform(post("/cadastro")
                .param("escritorio", "Escritório de Teste").param("email", "ana@escritorio.test").param("senha", SENHA))
                .andExpect(status().isForbidden());
        assertThat(usuarios.existsByEmail("ana@escritorio.test")).isFalse();
    }

    @Test
    void semSessaoNenhumaTelaDeDentroAbre() throws Exception {
        //Agir e conferir: quem nao entrou é levado para a tela de entrar, em qualquer endereço de dentro
        for (String endereco : new String[] {"/", "/enviar", "/empresas", "/notificacoes/1", "/notificacoes/1/pdf"}) {
            navegador.perform(get(endereco))
                    .andExpect(redirectedUrl("/entrar"));
        }
        //Os formularios tambem: mesmo com o codigo de proteção, sem sessão nada é gravado
        for (String endereco : new String[] {"/empresas", "/notificacoes/1/resolver"}) {
            navegador.perform(post(endereco).with(csrf()))
                    .andExpect(redirectedUrl("/entrar"));
        }
    }

    @Test
    void sairFechaASessao() throws Exception {
        //Agir e conferir: o botão de sair leva para a tela de entrar com o aviso
        navegador.perform(post("/sair").with(csrf()).with(user(NavegadorDeTeste.pessoaDo(0L))))
                .andExpect(redirectedUrl("/entrar?saiu"))
                .andExpect(unauthenticated());
    }
}
