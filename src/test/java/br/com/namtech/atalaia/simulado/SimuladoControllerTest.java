package br.com.namtech.atalaia.simulado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.historico.EventoDoCaso;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Simulado com cronômetro (US30): soltar, medir a primeira abertura, o aviso e o fechamento, e só contar no fim
@SpringBootTest
@Transactional
class SimuladoControllerTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    @Autowired
    private AvisoAoClienteRepository avisos;

    @Autowired
    private Historico historico;

    @Autowired
    private BancoDeTeste banco;

    //Quem solta o simulado (o dono)
    private MockMvc navegador;
    //Outra pessoa do mesmo escritório: a equipe, que não sabe que é teste
    private MockMvc colega;
    private Escritorio escritorio;
    private Empresa empresa;

    @BeforeEach
    void criaUmEscritorioComUmaEmpresaEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        colega = NavegadorDeTeste.logado(aplicacao, new UsuarioLogado("colega@escritorio.test", "", escritorio.getId()));
    }

    private Notificacao soltar() throws Exception {
        navegador.perform(post("/simulado").param("empresaId", empresa.getId().toString()))
                .andExpect(redirectedUrl("/simulado"));
        banco.gravar();
        return banco.ultima(Notificacao.class);
    }

    private List<String> acoes(Notificacao caso) {
        return historico.doCaso(caso.getId(), escritorio.getId()).stream().map(EventoDoCaso::getAcao).toList();
    }

    @Test
    void soltarCriaUmCasoDeTesteComPdfQueSeLeEOEventoNoHistorico() throws Exception {
        Notificacao teste = soltar();

        assertThat(teste.isSimulado()).isTrue();
        assertThat(teste.isPrecisaDeRevisao()).isFalse();
        assertThat(teste.getEmpresa().getId()).isEqualTo(empresa.getId());
        assertThat(acoes(teste)).containsExactly("Lançou uma notificação de teste");
        //O PDF é de verdade: o Leitor tira dele o mesmo texto, com a linha que diz que é exercício
        assertThat(new LeitorPdf().ler(teste.getArquivo()).texto()).contains("Documento de exercício gerado pelo Atalaia");
    }

    @Test
    void empresaDeOutroEscritorioERecusada() throws Exception {
        Empresa alheia = empresas.save(new Empresa(escritorios.save(new Escritorio("Outro")), "99999009004999", "Alheia"));

        navegador.perform(post("/simulado").param("empresaId", alheia.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPrimeiraAberturaMarcaAHoraUmaVezSo() throws Exception {
        Notificacao teste = soltar();

        //Agir: quem soltou abre (não conta), depois a colega abre duas vezes
        navegador.perform(get("/notificacoes/" + teste.getId())).andExpect(status().isOk());
        assertThat(banco.releer(Notificacao.class, teste.getId()).getAbertaEm()).isNull();
        colega.perform(get("/notificacoes/" + teste.getId())).andExpect(status().isOk());
        colega.perform(get("/notificacoes/" + teste.getId())).andExpect(status().isOk());

        //Conferir: a hora ficou marcada e o evento saiu uma vez só
        assertThat(banco.releer(Notificacao.class, teste.getId()).getAbertaEm()).isNotNull();
        assertThat(acoes(teste)).containsExactly("Lançou uma notificação de teste", "Abriu o caso");
    }

    @Test
    void casoComumNaoMarcaAbertura() throws Exception {
        Notificacao comum = notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto.", null,
                List.of(), List.of()));

        colega.perform(get("/notificacoes/" + comum.getId())).andExpect(status().isOk());

        assertThat(banco.releer(Notificacao.class, comum.getId()).getAbertaEm()).isNull();
        assertThat(acoes(comum)).isEmpty();
    }

    @Test
    void antesDeTerminarOCasoNaoContaQueEraTeste() throws Exception {
        Notificacao teste = soltar();

        navegador.perform(get("/notificacoes/" + teste.getId()))
                .andExpect(content().string(not(containsString("Era um simulado"))));
    }

    @Test
    void depoisDoAvisoOCasoMostraQueEraSimuladoEOsTempos() throws Exception {
        //Arrumar: a colega abriu, calculou o prazo e gerou dois avisos (12 e 40 minutos depois da chegada)
        Notificacao teste = soltar();
        colega.perform(get("/notificacoes/" + teste.getId()));
        teste = banco.releer(Notificacao.class, teste.getId());
        teste.registrarPrazo(LocalDate.now(), LocalDate.now().plusDays(10), List.of("Conta de teste."), "colega@escritorio.test");
        avisos.save(new AvisoAoCliente(teste, "token-do-simulado", "Resumo.", null, "colega@escritorio.test",
                teste.getRecebidaEm().plus(Duration.ofMinutes(12))));
        avisos.save(new AvisoAoCliente(teste, "token-mais-novo", "Resumo.", null, "colega@escritorio.test",
                teste.getRecebidaEm().plus(Duration.ofMinutes(40))));

        //Agir e conferir: o cartão do simulado com os tempos (o primeiro aviso conta, não o mais novo; a abertura foi
        //logo depois da chegada); responsável diz que ainda não existe
        navegador.perform(get("/notificacoes/" + teste.getId()))
                .andExpect(content().string(containsString("Era um simulado")))
                .andExpect(content().string(containsString("12 min")))
                .andExpect(content().string(not(containsString("40 min"))))
                .andExpect(content().string(containsString("<dt>Até alguém abrir</dt><dd class=\"num\">menos de 1 min</dd>")))
                .andExpect(content().string(containsString("o Atalaia ainda não tem responsável por caso")));
        //A página do cliente avisa que é teste, se o link escapar, e não pede confirmação nenhuma
        navegador.perform(get("/c/token-do-simulado"))
                .andExpect(content().string(containsString("Este aviso é um teste (simulado) do escritório.")))
                .andExpect(content().string(not(containsString("id=\"confirmar\""))));
    }

    @Test
    void depoisDeFecharATelaDoSimuladoMostraOsTempos() throws Exception {
        Notificacao teste = soltar();
        teste.resolver(Desfecho.ARQUIVADO, "Era teste.");

        navegador.perform(get("/simulado"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Testes terminados")))
                .andExpect(content().string(containsString("Gerou o aviso: ainda não")))
                .andExpect(content().string(containsString("Fechou: menos de 1 min")));
        navegador.perform(get("/notificacoes/" + teste.getId()))
                .andExpect(content().string(containsString("Era um simulado")));
    }

    @Test
    void simuladoNaoEntraNaContagemDeCasosDoCliente() throws Exception {
        soltar();

        assertThat(notificacoes.contarPorEmpresa(escritorio.getId(), Instant.now().minus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    void duracaoNoTamanhoQueImporta() {
        assertThat(TemposDoSimulado.duracao(Duration.ofSeconds(30))).isEqualTo("menos de 1 min");
        assertThat(TemposDoSimulado.duracao(Duration.ofMinutes(12))).isEqualTo("12 min");
        assertThat(TemposDoSimulado.duracao(Duration.ofMinutes(125))).isEqualTo("2 h 5 min");
        assertThat(TemposDoSimulado.duracao(Duration.ofHours(2))).isEqualTo("2 h");
        assertThat(TemposDoSimulado.duracao(Duration.ofHours(28))).isEqualTo("1 dia e 4 h");
        assertThat(TemposDoSimulado.duracao(Duration.ofDays(3))).isEqualTo("3 dias");
    }

    //Em andamento, a tela do simulado não diz qual é o caso: só a contagem, sem empresa e sem link
    @Test
    void simuladoEmAndamentoNaoAparecePeloNome() throws Exception {
        Notificacao teste = soltar();

        navegador.perform(get("/simulado"))
                .andExpect(content().string(containsString("1 teste em andamento.")))
                .andExpect(content().string(not(containsString("/notificacoes/" + teste.getId()))))
                .andExpect(content().string(not(containsString("Testes terminados"))));
    }

    //Se o cliente tentar confirmar o link de um simulado, nada é gravado
    @Test
    void clienteNaoConfirmaSimulado() throws Exception {
        Notificacao teste = soltar();
        teste = banco.releer(Notificacao.class, teste.getId());
        teste.registrarPrazo(LocalDate.now(), LocalDate.now().plusDays(10), List.of("Conta de teste."), "colega@escritorio.test");
        AvisoAoCliente aviso = avisos.save(new AvisoAoCliente(teste, "token-confirmar", "Resumo.", null,
                "colega@escritorio.test", Instant.now()));

        navegador.perform(post("/c/token-confirmar").param("resposta", aviso.opcoesDePrazo().get(0).toString()))
                .andExpect(status().is3xxRedirection());

        //releer limpa a mesa: a busca pelo token lê do banco
        banco.releer(Notificacao.class, teste.getId());
        assertThat(avisos.findByToken("token-confirmar").orElseThrow().getConfirmadoEmPorExtenso()).isNull();
    }
}
