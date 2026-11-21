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

import br.com.namtech.atalaia.BancoDeTeste;
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

    //Para conferir o banco inteiro (os repositórios só buscam dentro de um escritório)
    @Autowired
    private BancoDeTeste banco;

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
                .andExpect(content().string(containsString("Cadastrar uma empresa")))
                .andExpect(content().string(containsString("href=\"/empresas\"")))
                .andExpect(content().string(not(containsString("Nova notificação"))));
    }

    @Test
    void painelVazioConvidaAEnviar() throws Exception {
        //Arrumar: o escritório ja tem uma empresa, mas nenhuma notificação
        empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));

        //Agir e conferir: a tela explica o que fazer
        navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Subir a primeira notificação")))
                .andExpect(content().string(containsString("Nova notificação")));
    }

    @Test
    void casoSemPrazoPrecisaDeGenteEDizOQueFalta() throws Exception {
        //Arrumar: uma notificação conferida e outra que foi para revisao, as duas ainda sem prazo
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        notificacoes.save(new Notificacao(empresa, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "99999009000281", "Empresa Escaneada")),
                PDF, "", null, List.of(), List.of("Este PDF não tem texto.")));

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        //Conferir: as duas precisam de gente, cada uma com o que falta; os outros grupos nem aparecem
        assertThat(pagina).contains("Vez do escritório", "2 notificações", "Cobrança", "Sem tipo")
                .doesNotContain("Esperando o cliente", "Pronto para fechar");
        assertThat(linhaDa(pagina, "Empresa de Teste")).contains("etq falta\">Calcular o prazo<");
        assertThat(linhaDa(pagina, "Empresa Escaneada")).contains("etq falta\">Conferir a leitura<");
    }

    @Test
    void casosVaoParaOGrupoDeQuemTemOProximoPasso() throws Exception {
        //Arrumar: um caso sem aviso, um com o link valendo e um que o cliente confirmou com a data certa
        Instant agora = Instant.now();
        casoComPrazo(escritorio, "99999009001000", "Empresa Sem Aviso");
        avisoDo(casoComPrazo(escritorio, "99999009001001", "Empresa Aguardando"), "token-de-teste-aguardando", agora);
        avisoDo(casoComPrazo(escritorio, "99999009001002", "Empresa Confirmou"), "token-de-teste-confirmou", agora)
                .confirmar(LocalDate.now(Notificacao.TERESINA).plusDays(20), agora);
        banco.gravar();

        //Agir
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: os tres grupos, nesta ordem, cada caso no seu (pelo id do titulo de cada grupo)
        int gente = pagina.indexOf("id=\"titulo-gente\"");
        int cliente = pagina.indexOf("id=\"titulo-cliente\"");
        int fechar = pagina.indexOf("id=\"titulo-fechar\"");
        assertThat(gente).isNotNegative();
        assertThat(gente).isLessThan(cliente);
        assertThat(cliente).isLessThan(fechar);
        assertThat(pagina.indexOf("<strong>Empresa Sem Aviso")).isBetween(gente, cliente);
        assertThat(pagina.indexOf("<strong>Empresa Aguardando")).isBetween(cliente, fechar);
        assertThat(pagina.indexOf("<strong>Empresa Confirmou")).isGreaterThan(fechar);
        assertThat(linhaDa(pagina, "Empresa Sem Aviso")).contains("etq falta\">Avisar o cliente<");
        //Um caso em cada grupo: a contagem no singular
        assertThat(pagina).contains("1 notificação");
    }

    @Test
    void prazoDaProximaSemanaSaiEmAmarelo() throws Exception {
        //Arrumar: um caso que vence daqui a 5 dias
        Notificacao caso = casoComPrazo(escritorio, "99999009001000", "Empresa Perto");
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        caso.registrarPrazo(hoje, hoje.plusDays(5), List.of("Conta de teste."), "pessoa@escritorio.test");
        banco.gravar();

        //Agir e conferir: o nivel do meio da cor chega na pagina
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(linhaDa(pagina, "Empresa Perto")).contains("etq num perto\">Vence em 5 dias");
    }

    @Test
    void resolvidaSaiDaTelaInicialEApareceNoFiltro() throws Exception {
        //Arrumar: uma notificação que precisava de gente e ja foi resolvida
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        Notificacao resolvida = notificacoes.save(
                new Notificacao(empresa, PDF, "", null, List.of(), List.of("Este PDF não tem texto.")));
        resolvida.resolver(Desfecho.ARQUIVADO, "Conferido com o cliente.");

        //Agir: abre a tela inicial e depois o filtro das resolvidas
        String inicial = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();
        String filtro = navegador.perform(get("/").param("ver", "resolvidas"))
                .andReturn().getResponse().getContentAsString();

        //Conferir: a tela inicial diz que nada precisa de gente e aponta para o filtro, sem listar o caso;
        //no filtro ele aparece com o desfecho e a data em que foi resolvido
        assertThat(inicial).doesNotContain("/notificacoes/" + resolvida.getId() + "\"", "Vez do escritório")
                .contains("Nada pendente com o escritório", "href=\"/?ver=resolvidas\"", "aria-current=\"page\">Abertas");
        assertThat(filtro).contains("/notificacoes/" + resolvida.getId() + "\"", "Desfecho: Arquivado", "Resolvida em")
                .doesNotContain("Nada pendente com o escritório", "Recebida em")
                .contains("aria-current=\"page\">Resolvidas");
    }

    @Test
    void filtroDasResolvidasSemNenhumaCaiNasAbertas() throws Exception {
        //Arrumar: um caso aberto e nenhum resolvido
        casoComPrazo(escritorio, "99999009001000", "Empresa Aberta");
        banco.gravar();

        //Agir: alguem abre o filtro das resolvidas por um link antigo
        String pagina = navegador.perform(get("/").param("ver", "resolvidas"))
                .andReturn().getResponse().getContentAsString();

        //Conferir: a tela mostra as abertas, em vez de ficar vazia
        assertThat(pagina).contains("Empresa Aberta", "Vez do escritório");
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

        //Agir: abre o filtro das resolvidas
        String pagina = navegador.perform(get("/").param("ver", "resolvidas"))
                .andReturn().getResponse().getContentAsString();

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
        banco.gravar();

        //Agir
        String pagina = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: perto, longe, sem prazo; e a de amanhã vem com o alerta
        assertThat(pagina.indexOf("<strong>Empresa Perto")).isLessThan(pagina.indexOf("<strong>Empresa Longe"));
        assertThat(pagina.indexOf("<strong>Empresa Longe")).isLessThan(pagina.indexOf("<strong>Empresa Sem Prazo"));
        assertThat(pagina).contains("Vence amanhã").contains("Vence em 20 dias");
    }

    //Um caso aberto, conferido e com data limite daqui a 20 dias, de uma empresa nova do escritório dado
    @Test
    void escolherUmClienteMostraSoOsCasosDeleEContaAsAbertasDeCada() throws Exception {
        //Arrumar: a Padaria com dois casos abertos, a Oficina com um aberto e um resolvido, a Loja sem nenhum
        Empresa padaria = empresas.save(new Empresa(escritorio, "99999009002000", "Padaria Escolhida"));
        Empresa oficina = empresas.save(new Empresa(escritorio, "99999009002001", "Oficina Vizinha"));
        empresas.save(new Empresa(escritorio, "99999009002002", "Loja Sem Casos"));
        casoDa(padaria);
        casoDa(padaria);
        casoDa(oficina);
        casoDa(oficina).resolver(Desfecho.ARQUIVADO, "Conferido com o cliente.");
        banco.gravar();

        //Agir: sem filtro, a escolha do cliente aparece com as abertas de cada um
        String todos = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();

        //Conferir: as tres opções, com a contagem de abertas (a resolvida não conta)
        assertThat(todos).contains("Todos os clientes", "Padaria Escolhida · 2 abertas", "Oficina Vizinha · 1 aberta",
                "Loja Sem Casos · nenhuma aberta");

        //Agir: escolhe a Oficina
        String soOficina = navegador.perform(get("/").param("empresa", oficina.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        //Conferir: a lista só traz o caso aberto da Oficina (o nome da Padaria só aparece na opção da escolha),
        //a Oficina fica marcada, e o link das resolvidas (só a dela: 1) leva o filtro junto
        assertThat(soOficina).contains("1 notificação", "selected=\"selected\">Oficina Vizinha")
                .doesNotContain("<strong>Padaria Escolhida</strong>")
                .contains("href=\"/?ver=resolvidas&amp;empresa=" + oficina.getId() + "\"");
    }

    @Test
    void clienteSemCasosDizQueNaoHaNadaDele() throws Exception {
        //Arrumar: uma empresa com caso e outra sem nenhum
        casoDa(empresas.save(new Empresa(escritorio, "99999009002000", "Padaria Com Caso")));
        Empresa semCasos = empresas.save(new Empresa(escritorio, "99999009002001", "Loja Sem Casos"));

        //Agir e conferir: escolher a loja explica que não há nada dela, com o caminho de volta, e não diz "tudo certo".
        //Nem fala como escritório novo: a escolha do cliente continua lá para voltar
        navegador.perform(get("/").param("empresa", semCasos.getId().toString()))
                .andExpect(content().string(containsString("Nenhuma notificação deste cliente.")))
                .andExpect(content().string(containsString("Todos os clientes")))
                .andExpect(content().string(not(containsString("Nada pendente com o escritório agora."))))
                .andExpect(content().string(not(containsString("<strong>Padaria Com Caso</strong>"))));
    }

    @Test
    void empresaDeOutroEscritorioNoFiltroEIgnorada() throws Exception {
        //Arrumar: um caso meu e um caso de outro escritório
        casoDa(empresas.save(new Empresa(escritorio, "99999009002000", "Minha Padaria")));
        casoDa(empresas.save(new Empresa(escritorio, "99999009002001", "Minha Oficina")));
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        Empresa alheia = empresas.save(new Empresa(outro, "99999009002002", "Empresa Alheia"));
        casoDa(alheia);

        //Agir: alguém troca o numero no endereço pela empresa do outro escritório
        String pagina = navegador.perform(get("/").param("empresa", alheia.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        //E texto no lugar do numero também não vira erro
        navegador.perform(get("/").param("empresa", "abc")).andExpect(status().isOk());

        //Conferir: o filtro não vale, a tela mostra os meus dois casos e nada do outro escritório
        assertThat(pagina).contains("2 notificações", "<strong>Minha Padaria</strong>", "<strong>Minha Oficina</strong>")
                .doesNotContain("Empresa Alheia");
    }

    @Test
    void buscaAchaPeloNomeSemAcentoPeloCnpjEPeloTipo() throws Exception {
        //Arrumar: dois clientes, cada um com um caso de tipo diferente (#215)
        Empresa joao = empresas.save(new Empresa(escritorio, "99999009000100", "Mercadinho João"));
        Empresa outra = empresas.save(new Empresa(escritorio, "99999009000281", "Farmácia Central"));
        notificacoes.save(new Notificacao(joao, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        notificacoes.save(new Notificacao(outra, PDF, "texto", TipoDeDocumento.INTIMACAO, List.of(), List.of()));

        //Agir e conferir: sem acento e sem maiúscula, pelo pedaço do CNPJ com pontuação, e pelo tipo.
        //Olha só a lista de casos: a escolha do cliente, acima dela, sempre mostra os dois nomes
        assertThat(casosDaBusca("joao")).contains("Mercadinho João").doesNotContain("Farmácia Central");
        assertThat(casosDaBusca("09.000/281")).contains("Farmácia Central").doesNotContain("Mercadinho João");
        assertThat(casosDaBusca("cobranca")).contains("Mercadinho João").doesNotContain("Farmácia Central");
        //Nada encontrado: a tela diz e oferece voltar
        assertThat(navegador.perform(get("/").param("q", "xyz")).andReturn().getResponse().getContentAsString())
                .contains("Nada encontrado para");
    }

    //A lista de casos (da classe "casos" até o fim) depois de uma busca
    private String casosDaBusca(String busca) throws Exception {
        String pagina = navegador.perform(get("/").param("q", busca)).andReturn().getResponse().getContentAsString();
        return pagina.substring(pagina.indexOf("lista casos"));
    }

    @Test
    void paraComecarMostraOQueFaltaESomeComTudoFeito() throws Exception {
        //Conta nova: os três passos à vista, nenhum feito
        assertThat(navegador.perform(get("/")).andReturn().getResponse().getContentAsString())
                .contains("Para começar", "Cadastrar uma regra de prazo").doesNotContain("(feito)");
    }

    @Test
    void comUmaEmpresaSoNaoHaEscolhaDeCliente() throws Exception {
        //Arrumar: uma empresa, um caso
        casoDa(empresas.save(new Empresa(escritorio, "99999009002000", "Única Empresa")));

        //Agir e conferir: escolher entre um cliente só não ajuda; a escolha nem aparece
        navegador.perform(get("/")).andExpect(content().string(not(containsString("Todos os clientes"))));
    }

    private Notificacao casoDa(Empresa empresa) {
        return notificacoes.save(new Notificacao(empresa, PDF, "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
    }

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
        banco.gravar();

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
        //Caso resolvido nem aparece na tela inicial (fica no filtro das resolvidas)
        assertThat(pagina).doesNotContain("<strong>Empresa Resolvida");
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
    //Procura o nome em negrito: o nome sozinho também aparece na escolha do cliente, antes da lista
    private static String linhaDa(String pagina, String empresa) {
        int comeco = pagina.indexOf("<strong>" + empresa);
        return pagina.substring(comeco, pagina.indexOf("</a>", comeco));
    }
}
