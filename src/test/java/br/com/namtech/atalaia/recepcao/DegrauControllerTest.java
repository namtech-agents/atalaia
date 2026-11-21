package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.Degrau;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.historico.EventoDoCaso;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O degrau (US28): a tela mostra o degrau e o que costuma vir em seguida; o contador corrige com um clique
@SpringBootTest
@Transactional
class DegrauControllerTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    @Autowired
    private Historico historico;

    private MockMvc navegador;
    private Escritorio escritorio;
    private Notificacao caso;

    @BeforeEach
    void criaUmCasoComODegrauDaIaEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        caso = casoDo(escritorio, "99999009000115");
        caso.sugerirDegrau(Degrau.PEDIDO_DE_DOCUMENTO);
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    private Notificacao casoDo(Escritorio dono, String cnpj) {
        Empresa empresa = empresas.save(new Empresa(dono, cnpj, "Padaria de Teste"));
        return notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO,
                List.of(), List.of()));
    }

    private List<String> acoesDoHistorico() {
        return historico.doCaso(caso.getId(), escritorio.getId()).stream().map(EventoDoCaso::getAcao).toList();
    }

    @Test
    void telaMostraODegrauEOQueCostumaVirEmSeguida() throws Exception {
        String pagina = navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Se ninguém agir, o próximo degrau na escala do Atalaia é: Exigência sob pena.")))
                .andExpect(content().string(containsString("Sugerido por inteligência artificial.")))
                .andReturn().getResponse().getContentAsString();

        //Um botão só marcado como o atual, e é o do degrau do caso (a ordem dos atributos no HTML não importa)
        List<String> marcados = Pattern.compile("<button[^>]*aria-current=\"true\"[^>]*>").matcher(pagina).results()
                .map(achado -> achado.group()).toList();
        assertThat(marcados).hasSize(1);
        assertThat(marcados.get(0)).contains("value=\"PEDIDO_DE_DOCUMENTO\"", "disabled");
        assertThat(pagina).contains("✓ Pedido de documento");
    }

    @Test
    void oDegrauMaisAltoDizQueEOUltimo() throws Exception {
        caso.sugerirDegrau(Degrau.DEBITO_COBRADO);

        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(containsString("É o último degrau da escala do Atalaia.")));
    }

    @Test
    void casoSemDegrauPedeParaEscolher() throws Exception {
        caso.sugerirDegrau(null);

        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(containsString("Sem degrau")))
                .andExpect(content().string(containsString("Escolha o degrau:")));
    }

    @Test
    void corrigirMudaODegrauEFicaNoHistorico() throws Exception {
        //Agir: um clique no degrau certo
        navegador.perform(post("/notificacoes/" + caso.getId() + "/degrau").param("degrau", "AUTO_DE_INFRACAO"))
                .andExpect(redirectedUrl("/notificacoes/" + caso.getId() + "#titulo-degrau"));

        //Conferir: o degrau mudou e o histórico diz de qual para qual
        assertThat(notificacoes.buscar(caso.getId(), escritorio.getId()).orElseThrow().getDegrau())
                .isEqualTo(Degrau.AUTO_DE_INFRACAO);
        assertThat(acoesDoHistorico()).containsExactly("Corrigiu a gravidade: de Pedido de documento para Auto de infração");
        //A tela diz quem corrigiu, em vez de continuar dizendo que foi a IA; a lista mostra o degrau com o rótulo
        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(containsString("Corrigido por ")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Sugerido por inteligência artificial."))));
        navegador.perform(get("/")).andExpect(content().string(containsString("Gravidade: Auto de infração")));
    }

    @Test
    void tocarNoDegrauQueJaEstaNaoSujaOHistorico() throws Exception {
        navegador.perform(post("/notificacoes/" + caso.getId() + "/degrau").param("degrau", "PEDIDO_DE_DOCUMENTO"))
                .andExpect(status().is3xxRedirection());

        assertThat(acoesDoHistorico()).isEmpty();
    }

    @Test
    void casoResolvidoNaoMudaDeDegrau() throws Exception {
        caso.resolver(Desfecho.ARQUIVADO, "Arquivado pelo órgão.");

        navegador.perform(post("/notificacoes/" + caso.getId() + "/degrau").param("degrau", "DEBITO_COBRADO"))
                .andExpect(status().is3xxRedirection());

        assertThat(caso.getDegrau()).isEqualTo(Degrau.PEDIDO_DE_DOCUMENTO);
        assertThat(acoesDoHistorico()).isEmpty();
    }

    //Citação e exclusão seguem caminhos próprios (processo, regime): a escala não diz o que vem depois delas
    @Test
    void citacaoNaoMostraOProximoDegrau() throws Exception {
        Notificacao citacao = notificacoes.save(new Notificacao(caso.getEmpresa(), "%PDF-teste".getBytes(), "Texto.",
                TipoDeDocumento.CITACAO, List.of(), List.of()));
        citacao.sugerirDegrau(Degrau.EXIGENCIA_SOB_PENA);

        navegador.perform(get("/notificacoes/" + citacao.getId()))
                .andExpect(content().string(containsString("Exigência sob pena")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("próximo degrau"))));
    }

    //Pela mesma regra do resumo: a leitura que foi para revisão não decide a gravidade
    @Test
    void casoEmRevisaoNaoGuardaODegrauDaIa() {
        Notificacao emRevisao = new Notificacao(caso.getEmpresa(), "%PDF-teste".getBytes(), "Texto.",
                TipoDeDocumento.INTIMACAO, List.of(), List.of("A IA ficou em dúvida sobre o tipo do documento."));
        emRevisao.sugerirDegrau(Degrau.AVISO);

        assertThat(emRevisao.getDegrau()).isNull();
    }

    @Test
    void degrauForaDaListaERecusado() throws Exception {
        navegador.perform(post("/notificacoes/" + caso.getId() + "/degrau").param("degrau", "PRISAO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void casoDeOutroEscritorioRespondeComoSeNaoExistisse() throws Exception {
        Notificacao alheio = casoDo(escritorios.save(new Escritorio("Outro Escritório")), "99999009004999");

        navegador.perform(post("/notificacoes/" + alheio.getId() + "/degrau").param("degrau", "DEBITO_COBRADO"))
                .andExpect(status().isNotFound());
        assertThat(alheio.getDegrau()).isNull();
    }
}
