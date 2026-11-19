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

    private MockMvc navegador;
    private Escritorio escritorio;
    private Empresa empresa;

    @BeforeEach
    void criaUmEscritorioComUmaEmpresaEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
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
        assertThat(acoes(teste)).containsExactly("Soltou um simulado");
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

        //Agir: abre duas vezes
        navegador.perform(get("/notificacoes/" + teste.getId())).andExpect(status().isOk());
        navegador.perform(get("/notificacoes/" + teste.getId())).andExpect(status().isOk());

        //Conferir: a hora ficou marcada e o evento saiu uma vez só
        assertThat(banco.releer(Notificacao.class, teste.getId()).getAbertaEm()).isNotNull();
        assertThat(acoes(teste)).containsExactly("Soltou um simulado", "Abriu o caso");
    }

    @Test
    void casoComumNaoMarcaAbertura() throws Exception {
        Notificacao comum = notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto.", null,
                List.of(), List.of()));

        navegador.perform(get("/notificacoes/" + comum.getId())).andExpect(status().isOk());

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
        //Arrumar: o simulado foi aberto, o prazo calculado e o cliente avisado 12 minutos depois
        Notificacao teste = soltar();
        navegador.perform(get("/notificacoes/" + teste.getId()));
        teste = banco.releer(Notificacao.class, teste.getId());
        teste.registrarPrazo(LocalDate.now(), LocalDate.now().plusDays(10), List.of("Conta de teste."), "pessoa@escritorio.test");
        avisos.save(new AvisoAoCliente(teste, "token-do-simulado", "Resumo.", null, "pessoa@escritorio.test",
                Instant.now().plus(Duration.ofMinutes(12))));

        //Agir e conferir: o cartão do simulado com os tempos; responsável diz que ainda não existe
        navegador.perform(get("/notificacoes/" + teste.getId()))
                .andExpect(content().string(containsString("Era um simulado")))
                .andExpect(content().string(containsString("12 min")))
                .andExpect(content().string(containsString("o Atalaia ainda não tem responsável por caso")));
        //A página do cliente avisa que é teste, se o link escapar
        navegador.perform(get("/c/token-do-simulado"))
                .andExpect(content().string(containsString("Este aviso é um teste (simulado) do escritório.")));
    }

    @Test
    void depoisDeFecharATelaDoSimuladoMostraOsTempos() throws Exception {
        Notificacao teste = soltar();
        teste.resolver(Desfecho.ARQUIVADO, "Era teste.");

        navegador.perform(get("/simulado"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Simulados anteriores")))
                .andExpect(content().string(containsString("Avisou o cliente: ainda não")))
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
}
