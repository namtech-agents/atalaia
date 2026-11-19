package br.com.namtech.atalaia.prazo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;

//Só regra inventada: "Prazo de teste" e "Norma de Teste, art. 1" nao existem em lei nenhuma
@SpringBootTest
@Transactional
class RegraControllerTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private RegraCadastradaRepository regras;

    private MockMvc navegador;
    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    private static final String HOJE = LocalDate.now(RegraController.TERESINA).toString();

    //O formulario da tela. Cada teste diz só os campos que quer ver falhar; os outros vao preenchidos certo
    private MockHttpServletRequestBuilder cadastro(String nome, String dias, String artigo, String conferidaEm) {
        return post("/regras").param("nome", nome).param("dias", dias).param("contagem", "DIAS_UTEIS")
                .param("suspendeNoRecesso", "true").param("artigo", artigo).param("conferidaEm", conferidaEm);
    }

    private MockHttpServletRequestBuilder cadastro(String nome) {
        return cadastro(nome, "30", "Norma de Teste, art. 1", HOJE);
    }

    private RegraCadastrada guardada(Escritorio dono, String nome) {
        return regras.save(new RegraCadastrada(dono, new RegraDePrazo(nome, 30, TipoDeContagem.DIAS_UTEIS, false,
                "Norma de Teste, art. 1", LocalDate.now(RegraController.TERESINA))));
    }

    @Test
    void escritorioCadastraARegraEElaApareceNaLista() throws Exception {
        //Conferir: sem regra, a tela convida a cadastrar a primeira
        navegador.perform(get("/regras"))
                .andExpect(content().string(containsString("Cadastre a primeira regra")));

        //Agir
        navegador.perform(cadastro("Prazo de teste")).andExpect(redirectedUrl("/regras?cadastrada"));

        //Conferir: ficou guardada no escritório de quem cadastrou, do jeito que a Calculadora recebe
        assertThat(regras.findAllByEscritorioIdOrderByNome(escritorio.getId()))
                .singleElement()
                .satisfies(regra -> assertThat(regra.paraAConta()).isEqualTo(new RegraDePrazo("Prazo de teste", 30,
                        TipoDeContagem.DIAS_UTEIS, true, "Norma de Teste, art. 1",
                        LocalDate.now(RegraController.TERESINA))));
        navegador.perform(get("/regras").param("cadastrada", ""))
                .andExpect(content().string(containsString("Regra cadastrada.")))
                .andExpect(content().string(containsString("30 dias úteis")))
                .andExpect(content().string(containsString("para no recesso")))
                .andExpect(content().string(containsString("Norma de Teste, art. 1")));
    }

    @Test
    void regraSemArtigoSemConferenciaOuComDiasErradosNaoEntra() throws Exception {
        //Agir e conferir: cada pedido falha num campo, e a mensagem fala desse campo
        navegador.perform(cadastro("Sem artigo", "30", "   ", HOJE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escreva o artigo e a norma")));
        navegador.perform(cadastro("Sem conferência", "30", "Norma de Teste, art. 1", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Informe o dia em que a regra foi conferida")));
        String amanha = LocalDate.now(RegraController.TERESINA).plusDays(1).toString();
        navegador.perform(cadastro("Conferida amanhã", "30", "Norma de Teste, art. 1", amanha))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Não pode ser uma data no futuro")));
        for (String dias : new String[] { "0", "366", "trinta", "" }) {
            navegador.perform(cadastro("Dias errados", dias, "Norma de Teste, art. 1", HOJE))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("de 1 a 365")));
        }

        //Conferir: nada entrou
        assertThat(regras.findAllByEscritorioIdOrderByNome(escritorio.getId())).isEmpty();
    }

    @Test
    void nomeRepetidoNoEscritorioERecusado() throws Exception {
        //Arrumar
        guardada(escritorio, "Prazo de teste");

        //Agir e conferir
        navegador.perform(cadastro("Prazo de teste"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Já existe uma regra com este nome")));
    }

    @Test
    void conferenciaDeMaisDeSeisMesesApareceComoVencida() throws Exception {
        //Arrumar: uma regra conferida ha sete meses
        regras.save(new RegraCadastrada(escritorio, new RegraDePrazo("Prazo antigo", 15, TipoDeContagem.DIAS_CORRIDOS,
                false, "Norma de Teste, art. 2", LocalDate.now(RegraController.TERESINA).minusMonths(7))));

        //Agir e conferir
        navegador.perform(get("/regras"))
                .andExpect(content().string(containsString("15 dias corridos")))
                .andExpect(content().string(containsString("Conferência vencida")));
    }

    @Test
    void daParaExcluirARegraCadastradaErrada() throws Exception {
        //Arrumar
        RegraCadastrada regra = guardada(escritorio, "Prazo de teste");

        //Agir
        navegador.perform(post("/regras/" + regra.getId() + "/excluir")).andExpect(redirectedUrl("/regras?excluida"));

        //Conferir
        assertThat(regras.findAllByEscritorioIdOrderByNome(escritorio.getId())).isEmpty();
    }

    @Test
    void umEscritorioNaoVeNemExcluiARegraDeOutro() throws Exception {
        //Arrumar: outro escritório tem uma regra
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        RegraCadastrada alheia = guardada(outro, "Regra do vizinho");

        //Agir e conferir: nao aparece na lista, e excluir responde como se nao existisse
        navegador.perform(get("/regras")).andExpect(content().string(not(containsString("Regra do vizinho"))));
        navegador.perform(post("/regras/" + alheia.getId() + "/excluir")).andExpect(status().isNotFound());
        assertThat(regras.findAllByEscritorioIdOrderByNome(outro.getId())).hasSize(1);

        //Conferir: o mesmo nome pode existir nos dois escritórios
        navegador.perform(cadastro("Regra do vizinho")).andExpect(redirectedUrl("/regras?cadastrada"));
    }
}
