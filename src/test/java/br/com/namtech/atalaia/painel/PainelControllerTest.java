package br.com.namtech.atalaia.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Transactional: cada teste desfaz o que gravou no fim, para um nao atrapalhar o outro
@SpringBootTest
@Transactional
class PainelControllerTest {

    //O navegador de mentira é montado antes de cada teste, ja com a sessão de uma pessoa do escritório de teste
    private MockMvc navegador;

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    private static final byte[] PDF = "%PDF-teste".getBytes();

    @Test
    void escritorioSemEmpresaEConvidadoACadastrarAPrimeira() throws Exception {
        //Agir e conferir: escritório novo ainda nao tem para quem enviar; a tela aponta para o cadastro de empresas
        navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comece cadastrando a primeira empresa")))
                .andExpect(content().string(containsString("href=\"/empresas\"")))
                .andExpect(content().string(not(containsString("Enviar notificação"))));
    }

    @Test
    void painelVazioConvidaAEnviar() throws Exception {
        //Arrumar: o escritório ja tem uma empresa, mas nenhuma notificação
        empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));

        //Agir e conferir: a tela explica o que fazer
        navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma notificação recebida ainda")))
                .andExpect(content().string(containsString("Enviar notificação")));
    }

    @Test
    void painelSeparaOQuePrecisaDeGenteDoQueFoiConferido() throws Exception {
        //Arrumar: uma empresa inventada com uma notificação conferida e outra que foi para revisao
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        Notificacao conferida = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        Notificacao paraRevisao = notificacoes.save(
                new Notificacao(empresa, PDF, "", null, List.of(), List.of("Este PDF não tem texto.")));

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        //Conferir: os dois grupos aparecem, "precisa de gente" primeiro, cada um com o link da sua notificação
        assertThat(pagina).contains("Empresa de Teste", "Cobrança", "Sem tipo", "1 notificação");
        assertThat(pagina.indexOf("Precisa de gente")).isLessThan(pagina.indexOf("Lidas e conferidas"));
        assertThat(pagina.indexOf("/notificacoes/" + paraRevisao.getId()))
                .isBetween(pagina.indexOf("Precisa de gente"), pagina.indexOf("Lidas e conferidas"));
        assertThat(pagina.indexOf("/notificacoes/" + conferida.getId()))
                .isGreaterThan(pagina.indexOf("Lidas e conferidas"));
    }

    @Test
    void notificacaoResolvidaSaiDosGruposAbertosEVaiParaOFim() throws Exception {
        //Arrumar: uma notificação que precisava de gente e ja foi resolvida
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        Notificacao resolvida = notificacoes.save(
                new Notificacao(empresa, PDF, "", null, List.of(), List.of("Este PDF não tem texto.")));
        resolvida.resolver(Desfecho.ARQUIVADO, "Conferido com o cliente.");

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: ela aparece só no grupo das resolvidas, com o desfecho e a data em que foi resolvida
        assertThat(pagina).doesNotContain("Precisa de gente", "Recebida em")
                .contains("Nada precisa de gente", "Desfecho: Arquivado", "Resolvida em");
        assertThat(pagina.indexOf("/notificacoes/" + resolvida.getId() + "\""))
                .isGreaterThan(pagina.indexOf("Resolvidas"));
    }

    @Test
    void resolvidasVemDaUltimaResolvidaParaAPrimeira() throws Exception {
        //Arrumar: duas notificações; a que chegou primeiro é resolvida por ultimo
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        Notificacao chegouPrimeiro = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        Notificacao chegouDepois = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        chegouDepois.resolver(Desfecho.ARQUIVADO, "Resolvida antes.");
        //Uma pausa curta, para as duas resoluções nao caírem no mesmo instante
        Thread.sleep(20);
        chegouPrimeiro.resolver(Desfecho.OUTRO, "Resolvida por ultimo.");

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: a ultima resolvida aparece em cima, mesmo tendo chegado antes
        assertThat(pagina.indexOf("/notificacoes/" + chegouPrimeiro.getId() + "\""))
                .isLessThan(pagina.indexOf("/notificacoes/" + chegouDepois.getId() + "\""));
    }

    @Test
    void listaVemDaMaisNovaParaAMaisAntiga() throws Exception {
        //Arrumar: duas notificações conferidas da mesma empresa, uma depois da outra
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        Notificacao primeira = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        Notificacao segunda = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.INTIMACAO, List.of(), List.of()));

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: a que chegou por ultimo aparece em cima (aspas no fim: "/notificacoes/1" nao casa com "/notificacoes/10")
        assertThat(pagina.indexOf("/notificacoes/" + segunda.getId() + "\""))
                .isLessThan(pagina.indexOf("/notificacoes/" + primeira.getId() + "\""));
    }
}
