package br.com.namtech.atalaia.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;

@SpringBootTest
@Transactional
class EmpresaControllerTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    private MockMvc navegador;
    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    @Test
    void escritorioNovoComecaSemEmpresaECadastraAPrimeira() throws Exception {
        //Conferir: a tela convida a cadastrar a primeira
        navegador.perform(get("/empresas"))
                .andExpect(content().string(containsString("Cadastre a primeira empresa")));

        //Agir: cadastra, com o CNPJ digitado com a pontuação
        navegador.perform(post("/empresas").param("nome", "  Padaria de Teste  ").param("cnpj", "99.999.009/0001-00"))
                .andExpect(redirectedUrl("/empresas"));

        //Conferir: ficou guardada no escritório de quem cadastrou, só com as 14 posições, e aparece na lista
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId()))
                .singleElement()
                .satisfies(empresa -> {
                    assertThat(empresa.getNome()).isEqualTo("Padaria de Teste");
                    assertThat(empresa.getCnpj()).isEqualTo("99999009000100");
                });
        navegador.perform(get("/empresas"))
                .andExpect(content().string(containsString("Padaria de Teste")))
                .andExpect(content().string(containsString("99.999.009/0001-00")));
    }

    @Test
    void cnpjIncompletoOuRepetidoERecusadoComMensagem() throws Exception {
        //Arrumar: uma empresa ja cadastrada
        empresas.save(new Empresa(escritorio, "99999009000100", "Padaria de Teste"));

        //Agir e conferir: CNPJ faltando posição, e depois o mesmo CNPJ de novo
        navegador.perform(post("/empresas").param("nome", "Oficina de Teste").param("cnpj", "99.999.009/0001"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escreva o CNPJ completo")));
        navegador.perform(post("/empresas").param("nome", "Oficina de Teste").param("cnpj", "99999009000100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("já está cadastrado")));

        //Conferir: continua só a primeira
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId())).hasSize(1);
    }

    @Test
    void mesmoCnpjPodeSerClienteDeDoisEscritorios() throws Exception {
        //Arrumar: outro escritório ja tem uma empresa com este CNPJ
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        empresas.save(new Empresa(outro, "99999009000100", "Padaria de Teste"));

        //Agir e conferir: o escritório de quem entrou cadastra a mesma empresa sem erro
        navegador.perform(post("/empresas").param("nome", "Padaria de Teste").param("cnpj", "99999009000100"))
                .andExpect(redirectedUrl("/empresas"));
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId())).hasSize(1);
    }
}
