package br.com.namtech.atalaia.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
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

    @Autowired
    private AvisoAoClienteRepository avisos;

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

    @Test
    void prazoMaisPertoVemEmCimaESemPrazoFicaEmbaixo() throws Exception {
        //Arrumar: tres notificações abertas e conferidas. A do meio chegou primeiro e tem o prazo mais perto
        Notificacao longe = notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "99999009000100",
                "Empresa Longe")), PDF, "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        Notificacao perto = notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "99999009000281",
                "Empresa Perto")), PDF, "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "99999009000362",
                "Empresa Sem Prazo")), PDF, "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        longe.registrarPrazo(hoje, hoje.plusDays(20), List.of("Conta de teste."), "pessoa@escritorio.test");
        perto.registrarPrazo(hoje, hoje.plusDays(1), List.of("Conta de teste."), "pessoa@escritorio.test");
        //Grava ja: a lista do painel le direto do banco
        notificacoes.flush();

        //Agir
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: perto, longe, sem prazo; e a de amanhã vem com o alerta
        assertThat(pagina.indexOf("Empresa Perto")).isLessThan(pagina.indexOf("Empresa Longe"));
        assertThat(pagina.indexOf("Empresa Longe")).isLessThan(pagina.indexOf("Empresa Sem Prazo"));
        assertThat(pagina).contains("Vence amanhã").contains("Vence em 20 dias");
    }

    //Um caso aberto, conferido e com data limite daqui a 20 dias, de uma empresa nova do escritório dado
    private Notificacao casoComPrazo(Escritorio dono, String cnpj, String nomeDaEmpresa) {
        Notificacao caso = notificacoes.save(new Notificacao(empresas.save(new Empresa(dono, cnpj, nomeDaEmpresa)),
                PDF, "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        caso.registrarPrazo(hoje, hoje.plusDays(20), List.of("Conta de teste."), "pessoa@escritorio.test");
        return caso;
    }

    private AvisoAoCliente avisoDo(Notificacao caso, String token, Instant criadoEm) {
        return avisos.save(new AvisoAoCliente(caso, token, "Resumo.", null, "pessoa@escritorio.test", criadoEm));
    }

    @Test
    void listaMostraSeOClienteFoiAvisadoESeJaConfirmou() throws Exception {
        //Arrumar: um caso para cada situação do aviso
        Instant agora = Instant.now();
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        casoComPrazo(escritorio, "99999009001000", "Empresa Sem Aviso");
        //Um aviso antigo foi substituído: na lista vale o mais novo
        Notificacao aguardando = casoComPrazo(escritorio, "99999009001001", "Empresa Aguardando");
        avisoDo(aguardando, "token-de-teste-antigo", agora.minusSeconds(60)).substituir(agora);
        avisoDo(aguardando, "token-de-teste-aguardando", agora);
        avisoDo(casoComPrazo(escritorio, "99999009001002", "Empresa Acertou"), "token-de-teste-acertou", agora)
                .confirmar(hoje.plusDays(20), agora);
        avisoDo(casoComPrazo(escritorio, "99999009001003", "Empresa Errou"), "token-de-teste-errou", agora)
                .confirmar(hoje.plusDays(27), agora);
        avisoDo(casoComPrazo(escritorio, "99999009001004", "Empresa Vencida"), "token-de-teste-vencido",
                agora.minus(31, ChronoUnit.DAYS));
        //O cliente confirmou certo, e depois a conta do prazo foi refeita: ele viu outra data
        Notificacao mudou = casoComPrazo(escritorio, "99999009001005", "Empresa Mudou");
        avisoDo(mudou, "token-de-teste-mudou", agora).confirmar(hoje.plusDays(20), agora);
        mudou.registrarPrazo(hoje, hoje.plusDays(13), List.of("Conta refeita."), "pessoa@escritorio.test");
        //Caso ja resolvido nao mostra o aviso: nao ha mais o que esperar do cliente
        Notificacao resolvido = casoComPrazo(escritorio, "99999009001006", "Empresa Resolvida");
        avisoDo(resolvido, "token-de-teste-resolvido", agora);
        resolvido.resolver(Desfecho.ARQUIVADO, "Arquivado pelo órgão.");
        notificacoes.flush();

        //Agir
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: cada linha diz a situação do aviso; só quem pede ação do escritório vem com o alerta
        assertThat(linhaDa(pagina, "Empresa Sem Aviso")).doesNotContain(">Aguardando o cliente", ">Cliente confirmou",
                ">Link vencido");
        assertThat(linhaDa(pagina, "Empresa Aguardando")).contains("etq\">Aguardando o cliente<")
                .doesNotContain("Substituído");
        assertThat(linhaDa(pagina, "Empresa Acertou")).contains("etq\">Cliente confirmou<");
        assertThat(linhaDa(pagina, "Empresa Errou")).contains("etq atencao\">Cliente confirmou, com a data errada<");
        assertThat(linhaDa(pagina, "Empresa Vencida")).contains("etq atencao\">Link vencido sem confirmação<");
        assertThat(linhaDa(pagina, "Empresa Mudou"))
                .contains("etq atencao\">Cliente confirmou, mas a data limite mudou<");
        assertThat(linhaDa(pagina, "Empresa Resolvida")).doesNotContain(">Aguardando o cliente");
    }

    @Test
    void buscaDosAvisosDaListaNaoTrazODeOutroEscritorio() {
        //Arrumar: um aviso neste escritório e outro no vizinho
        Escritorio vizinho = escritorios.save(new Escritorio("Outro Escritório"));
        AvisoAoCliente daqui = avisoDo(casoComPrazo(escritorio, "99999009001000", "Empresa Daqui"),
                "token-de-teste-daqui", Instant.now());
        avisoDo(casoComPrazo(vizinho, "99999009001001", "Empresa do Vizinho"), "token-de-teste-vizinho", Instant.now());

        //Agir e conferir: cada escritório recebe só o seu
        assertThat(avisos.dosCasosAbertos(escritorio.getId())).containsExactly(daqui);
    }

    //O pedaço da pagina com a linha de uma empresa: do nome dela ate o fim do link
    private static String linhaDa(String pagina, String empresa) {
        int comeco = pagina.indexOf(empresa);
        return pagina.substring(comeco, pagina.indexOf("</a>", comeco));
    }
}
