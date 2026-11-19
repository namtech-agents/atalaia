package br.com.namtech.atalaia.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O aviso ao cliente de ponta a ponta: o escritório gera o link (com sessão) e o cliente confirma (sem sessão)
@SpringBootTest
@Transactional
class AvisoAoClienteTest {

    private static final LocalDate PRAZO = LocalDate.of(2026, 12, 10);

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

    //O navegador do escritório, com a sessão aberta, e o do cliente, que nao tem conta
    private MockMvc escritorioLogado;
    private MockMvc cliente;
    private Escritorio escritorio;
    private Notificacao notificacao;

    @BeforeEach
    void criaOEscritorioComUmaNotificacaoComPrazo() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        escritorioLogado = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        cliente = MockMvcBuilders.webAppContextSetup(aplicacao).apply(springSecurity()).build();
        notificacao = notificacaoDo(escritorio);
        notificacao.registrarPrazo(LocalDate.of(2026, 11, 10), PRAZO, List.of("Conta de teste."),
                "pessoa@escritorio.test");
    }

    private Notificacao notificacaoDo(Escritorio dono) {
        Empresa empresa = empresas.save(new Empresa(dono, "99999009000100", "Padaria de Teste"));
        return notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto de teste.",
                TipoDeDocumento.INTIMACAO, List.of(), List.of()));
    }

    //O escritório gera o link pela tela e o teste pega o aviso que nasceu
    private AvisoAoCliente avisa(String resumo, String documentos) throws Exception {
        escritorioLogado.perform(post("/notificacoes/" + notificacao.getId() + "/aviso").param("resumo", resumo)
                .param("documentos", documentos))
                .andExpect(redirectedUrl("/notificacoes/" + notificacao.getId() + "#titulo-aviso"));
        return avisos.doCaso(notificacao.getId(), escritorio.getId()).get(0);
    }

    //Uma das duas datas erradas da pergunta
    private LocalDate dataErrada(AvisoAoCliente aviso) {
        return aviso.opcoesDePrazo().stream().filter(opcao -> !opcao.equals(PRAZO)).findFirst().orElseThrow();
    }

    @Test
    void clienteAbreOLinkSemContaEVeOResumoOPrazoEOsDocumentos() throws Exception {
        //Agir: o escritório avisa
        AvisoAoCliente aviso = avisa("Chegou uma intimação.\r\n\r\nSepare os documentos.", " Contrato social \r\n\r\nBalanço de 2025");

        //Conferir: o token tem as 43 letras de 32 bytes sorteados, e a pergunta traz a data limite entre tres datas
        assertThat(aviso.getToken()).hasSize(43);
        assertThat(aviso.opcoesDePrazo()).hasSize(3).contains(PRAZO);
        //A tela do escritório mostra o link e a situação
        escritorioLogado.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("Aguardando o cliente")))
                .andExpect(content().string(containsString("/c/" + aviso.getToken())));
        //O cliente, sem sessão, ve o aviso: empresa, escritório, resumo, prazo e documentos
        cliente.perform(get("/c/" + aviso.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Padaria de Teste")))
                .andExpect(content().string(containsString("Escritório de Teste")))
                .andExpect(content().string(containsString("Chegou uma intimação.\nSepare os documentos.")))
                .andExpect(content().string(containsString("10/12/2026")))
                .andExpect(content().string(containsString("Contrato social")))
                .andExpect(content().string(containsString("Balanço de 2025")))
                .andExpect(content().string(containsString("Confirmar que recebi")))
                //e nenhum caminho para o resto do Atalaia
                .andExpect(content().string(not(containsString("Notificações"))));
    }

    @Test
    void clienteQueAcertaAPerguntaDeixaORegistroDeCiencia() throws Exception {
        //Arrumar
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");

        //Agir: o cliente marca a data limite e confirma
        cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", PRAZO.toString()))
                .andExpect(redirectedUrl("/c/" + aviso.getToken()));

        //Conferir: o registro ficou gravado, e as duas telas mostram
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.CONFIRMADO);
        assertThat(aviso.acertouOPrazo()).isTrue();
        cliente.perform(get("/c/" + aviso.getToken()))
                .andExpect(content().string(containsString("Recebimento confirmado")))
                .andExpect(content().string(not(containsString("Confirmar que recebi"))));
        escritorioLogado.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("Cliente confirmou")))
                .andExpect(content().string(containsString("marcou a data limite certa")))
                //confirmado e com a mesma data: nao ha por que fazer outro aviso
                .andExpect(content().string(not(containsString("Fazer um aviso novo"))));

        //Agir de novo: confirmar outra vez, com outra data, nao troca o registro
        cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", dataErrada(aviso).toString()));
        assertThat(aviso.getResposta()).isEqualTo(PRAZO);
    }

    @Test
    void clienteQueErraAPerguntaConfirmaMesmoAssimEOEscritorioFicaSabendo() throws Exception {
        //Arrumar
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");

        //Agir
        cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", dataErrada(aviso).toString()));

        //Conferir: confirmou, o link explica a data certa de novo e o escritório ve que vale uma ligação
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.CONFIRMADO);
        assertThat(aviso.acertouOPrazo()).isFalse();
        cliente.perform(get("/c/" + aviso.getToken()))
                .andExpect(content().string(containsString("mas a data limite é")));
        escritorioLogado.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("Vale uma ligação")));
    }

    @Test
    void respostaQueNaoEstaEntreAsTresDatasNaoConfirma() throws Exception {
        //Arrumar
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");

        //Agir e conferir: sem resposta, com texto e com uma data que nao foi mostrada
        for (String resposta : new String[] { "", "amanhã", "2030-01-01" }) {
            cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", resposta))
                    .andExpect(flash().attribute("erro", "Marque uma das datas para confirmar."));
        }
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.AGUARDANDO);
    }

    @Test
    void linkQueNaoExisteResponde404EConfirmarExigeOCodigoDoFormulario() throws Exception {
        //Arrumar
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");

        //Agir e conferir: token inventado nao mostra nada; pedido sem o codigo do formulario (CSRF) é barrado
        cliente.perform(get("/c/token-que-nao-existe")).andExpect(status().isNotFound());
        cliente.perform(post("/c/token-que-nao-existe").with(csrf()).param("resposta", PRAZO.toString()))
                .andExpect(status().isNotFound());
        cliente.perform(post("/c/" + aviso.getToken()).param("resposta", PRAZO.toString()))
                .andExpect(status().isForbidden());
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.AGUARDANDO);
    }

    @Test
    void avisoNovoTiraAValidadeDoLinkAnterior() throws Exception {
        //Arrumar: dois avisos seguidos
        AvisoAoCliente primeiro = avisa("Primeiro resumo.", "");
        AvisoAoCliente segundo = avisa("Segundo resumo.", "");

        //Agir: o cliente tenta confirmar pelo link antigo
        cliente.perform(post("/c/" + primeiro.getToken()).with(csrf()).param("resposta", PRAZO.toString()));

        //Conferir: o antigo nao confirma e explica; o novo segue aguardando
        assertThat(segundo.getToken()).isNotEqualTo(primeiro.getToken());
        assertThat(primeiro.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.SUBSTITUIDO);
        assertThat(segundo.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.AGUARDANDO);
        cliente.perform(get("/c/" + primeiro.getToken()))
                .andExpect(content().string(containsString("substituído por um mais novo")))
                .andExpect(content().string(not(containsString("Confirmar que recebi"))));
    }

    @Test
    void linkVencidoNaoConfirma() throws Exception {
        //Arrumar: um aviso feito ha 31 dias (o link vale 30)
        AvisoAoCliente antigo = avisos.save(new AvisoAoCliente(notificacao, "token-de-teste-vencido", "Resumo antigo.",
                null, "pessoa@escritorio.test", Instant.now().minus(31, ChronoUnit.DAYS)));

        //Agir
        cliente.perform(post("/c/" + antigo.getToken()).with(csrf()).param("resposta", PRAZO.toString()));

        //Conferir
        assertThat(antigo.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.VENCIDO);
        cliente.perform(get("/c/" + antigo.getToken()))
                .andExpect(content().string(containsString("Este link venceu")));
    }

    @Test
    void semPrazoOuSemResumoOAvisoNaoNasce() throws Exception {
        //Arrumar: outra notificação, sem prazo calculado
        Notificacao semPrazo = notificacoes.save(new Notificacao(notificacao.getEmpresa(), "%PDF-teste".getBytes(),
                "Texto de teste.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));

        //Agir e conferir
        escritorioLogado.perform(post("/notificacoes/" + semPrazo.getId() + "/aviso").param("resumo", "Resumo."))
                .andExpect(flash().attribute("erroDoAviso", containsString("Calcule a data limite")));
        escritorioLogado.perform(post("/notificacoes/" + notificacao.getId() + "/aviso").param("resumo", "  \r\n "))
                .andExpect(flash().attribute("erroDoAviso", containsString("Escreva o resumo")));
        escritorioLogado.perform(post("/notificacoes/" + notificacao.getId() + "/aviso")
                .param("resumo", "x".repeat(601)))
                .andExpect(flash().attribute("erroDoAviso", containsString("Escreva o resumo")));
        assertThat(avisos.doCaso(semPrazo.getId(), escritorio.getId())).isEmpty();
        assertThat(avisos.doCaso(notificacao.getId(), escritorio.getId())).isEmpty();
        //Sem prazo, a tela explica o que falta e nao mostra o formulario do aviso
        escritorioLogado.perform(get("/notificacoes/" + semPrazo.getId()))
                .andExpect(content().string(containsString("Calcule a data limite primeiro")))
                .andExpect(content().string(not(containsString("Gerar o link do cliente"))));
    }

    @Test
    void umEscritorioNaoAvisaNemVeOAvisoDaNotificacaoDeOutro() throws Exception {
        //Arrumar: o aviso é deste escritório; quem tenta é o vizinho
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        MockMvc vizinho = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(outro.getId()));

        //Agir e conferir: para o vizinho a notificação nao existe, e a busca dos avisos vem vazia
        vizinho.perform(post("/notificacoes/" + notificacao.getId() + "/aviso").param("resumo", "Resumo do vizinho."))
                .andExpect(status().isNotFound());
        assertThat(avisos.doCaso(notificacao.getId(), outro.getId())).isEmpty();
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.AGUARDANDO);
    }

    @Test
    void seAContaDoPrazoForRefeitaATelaPedeUmAvisoNovo() throws Exception {
        //Arrumar: o cliente confirmou, e depois a data limite mudou
        AvisoAoCliente aviso = avisa("Chegou uma intimação.", "");
        cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", PRAZO.toString()));
        notificacao.registrarPrazo(LocalDate.of(2026, 11, 10), PRAZO.plusDays(5), List.of("Conta refeita."),
                "pessoa@escritorio.test");

        //Agir e conferir: a tela avisa que o cliente viu outra data e deixa fazer um aviso novo
        escritorioLogado.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("A data limite mudou depois deste aviso")))
                .andExpect(content().string(containsString("Fazer um aviso novo")));
        //O aviso novo nao apaga o registro de ciência do anterior
        avisa("Resumo com a data nova.", "");
        assertThat(aviso.situacao(Instant.now())).isEqualTo(SituacaoDoAviso.CONFIRMADO);
        assertThat(avisos.doCaso(notificacao.getId(), escritorio.getId())).hasSize(2);
    }
}
