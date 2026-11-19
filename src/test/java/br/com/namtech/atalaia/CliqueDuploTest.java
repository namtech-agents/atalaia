package br.com.namtech.atalaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.conta.Usuario;
import br.com.namtech.atalaia.conta.UsuarioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;

//O clique duplo (ou duas pessoas ao mesmo tempo) num cadastro: os dois pedidos perguntam "isto já existe?",
//os dois ouvem "não", e os dois tentam gravar. Quem barra o segundo é a regra do banco (unique), e a pessoa
//precisa ver a mensagem de repetido, não uma página de erro.
//Duas threads disputando de verdade dariam um teste que às vezes passa e às vezes não. Aqui o segundo pedido
//é montado de propósito: o registro já está gravado, e o espião (MockitoSpyBean) faz a pergunta responder "não".
//Sem Transactional: o banco precisa recusar de verdade, e um erro de banco estraga a transação do teste.
//Por isso o teste apaga no fim o que criou
@SpringBootTest
@AutoConfigureMockMvc
class CliqueDuploTest {

    //Espião: o repositório de verdade, só com a resposta que o teste trocar
    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @MockitoSpyBean
    private EmpresaRepository empresas;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private BancoDeTeste banco;

    //Sem sessão: é como chega quem vai criar a conta
    @Autowired
    private MockMvc navegador;

    @Autowired
    private WebApplicationContext aplicacao;

    private Escritorio escritorio;
    private Usuario usuario;
    private Empresa empresa;

    @AfterEach
    void apagaOQueCriou() {
        //Na ordem contrária à de criar: o banco não deixa apagar o escritório antes do que aponta para ele
        if (empresa != null) {
            banco.apagar(Empresa.class, empresa.getId());
        }
        if (usuario != null) {
            banco.apagar(Usuario.class, usuario.getId());
        }
        if (escritorio != null) {
            banco.apagar(Escritorio.class, escritorio.getId());
        }
    }

    @Test
    void segundoCadastroComOMesmoEmailMostraAMensagem() throws Exception {
        //Arrumar: a conta do primeiro clique já gravada, e a conferência do segundo sem enxergar ela
        escritorio = escritorios.save(new Escritorio("Escritório do Primeiro Clique"));
        usuario = usuarios.save(new Usuario(escritorio, "dupla@clique.test", "{noop}tanto faz"));
        doReturn(false).when(usuarios).existsByEmail(anyString());
        long contas = banco.contar(Usuario.class);
        long escritoriosAntes = banco.contar(Escritorio.class);

        //Agir: o segundo clique
        navegador.perform(post("/cadastro").with(csrf())
                        .param("escritorio", "Escritório do Segundo Clique").param("email", "dupla@clique.test")
                        .param("senha", "uma frase comprida").param("senhaRepetida", "uma frase comprida"))
                //Conferir: a tela de cadastro com a mensagem de e-mail repetido
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Já existe uma conta com este e-mail.")));

        //Conferir: nada ficou pela metade (o escritório do segundo clique foi desfeito junto com a conta)
        assertThat(banco.contar(Usuario.class)).isEqualTo(contas);
        assertThat(banco.contar(Escritorio.class)).isEqualTo(escritoriosAntes);
    }

    @Test
    void segundoCadastroDaMesmaEmpresaMostraAMensagem() throws Exception {
        //Arrumar: a empresa do primeiro clique já gravada, e a conferência do segundo sem enxergar ela
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        doReturn(false).when(empresas).existsByEscritorioIdAndCnpj(any(), anyString());
        long antes = banco.contar(Empresa.class);

        //Agir: o segundo clique
        NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()))
                .perform(post("/empresas").param("nome", "Padaria de Teste").param("cnpj", "99.999.009/0001-15"))
                //Conferir: o formulário com a mensagem de CNPJ repetido
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este CNPJ já está cadastrado no escritório.")));
        assertThat(banco.contar(Empresa.class)).isEqualTo(antes);
    }
}
