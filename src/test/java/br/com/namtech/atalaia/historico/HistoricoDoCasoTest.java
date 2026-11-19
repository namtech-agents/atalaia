package br.com.namtech.atalaia.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.Datas;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.prazo.RegraCadastrada;
import br.com.namtech.atalaia.prazo.RegraCadastradaRepository;
import br.com.namtech.atalaia.prazo.RegraDePrazo;
import br.com.namtech.atalaia.prazo.TipoDeContagem;

//O caso inteiro, do envio ao fim, pelas mesmas rotas que a tela usa: cada passo deixa uma linha no historico
@SpringBootTest
@Transactional
class HistoricoDoCasoTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    @Autowired
    private RegraCadastradaRepository regras;

    @Autowired
    private AvisoAoClienteRepository avisos;

    @Autowired
    private Historico historico;

    //A IA de mentira: responde sempre a mesma analise, com um dado que esta mesmo no PDF de exemplo
    @MockitoBean
    private ModeloDeIA ia;

    private Escritorio escritorio;
    private Empresa empresa;
    private MockMvc escritorioNavegando;
    //O cliente nao tem conta: abre o link sem sessão
    private MockMvc cliente;

    @BeforeEach
    void arrumaOEscritorioEOCliente() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        escritorioNavegando = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        cliente = MockMvcBuilders.webAppContextSetup(aplicacao).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        when(ia.responder(anyString())).thenReturn("""
                {"tipo": "INTIMACAO", "confianca": "alta", "dados": [
                  {"campo": "prazo", "valor": "30 dias", "trecho": "Prazo: 30 dias a contar da ciência"}],
                 "resumo": "A Receita pediu os livros da empresa."}
                """);
    }

    @Test
    void cadaPassoDoCasoFicaNoHistoricoComQuemFezENaOrdem() throws Exception {
        //Arrumar: uma regra de prazo conferida hoje
        RegraCadastrada regra = regras.save(new RegraCadastrada(escritorio, new RegraDePrazo("Prazo de teste", 10,
                TipoDeContagem.DIAS_UTEIS, false, "Norma de Teste, art. 1", LocalDate.now(Notificacao.TERESINA))));
        byte[] bytes = getClass().getResourceAsStream("/pdfs/notificacao-acentuada.pdf").readAllBytes();

        //Agir: o caminho inteiro. Envia o PDF...
        escritorioNavegando.perform(multipart("/enviar")
                .file(new MockMultipartFile("arquivo", "intimacao.pdf", MediaType.APPLICATION_PDF_VALUE, bytes))
                .param("empresaId", empresa.getId().toString()));
        Notificacao caso = notificacoes.findAll().getLast();
        String endereco = "/notificacoes/" + caso.getId();
        //...calcula o prazo com a ciência de hoje...
        escritorioNavegando.perform(post(endereco + "/prazo").param("regraId", regra.getId().toString())
                .param("ciencia", LocalDate.now(Notificacao.TERESINA).toString()));
        //...gera o link para o cliente...
        escritorioNavegando.perform(post(endereco + "/aviso").param("resumo", "A Receita pediu os livros."));
        AvisoAoCliente aviso = avisos.doCaso(caso.getId(), escritorio.getId()).getFirst();
        //...o cliente confirma marcando a data certa...
        cliente.perform(post("/c/" + aviso.getToken()).with(csrf()).param("resposta", aviso.getPrazo().toString()));
        //...e o escritorio fecha o caso
        escritorioNavegando.perform(post(endereco + "/resolver").param("desfecho", "ARQUIVADO")
                .param("observacao", "Livros entregues."));

        //Conferir: cinco eventos, na ordem, cada um com quem fez ("quem | o que")
        List<String> linhas = historico.doCaso(caso.getId(), escritorio.getId()).stream()
                .map(evento -> evento.getQuem() + " | " + evento.getAcao()).toList();
        assertThat(linhas).hasSize(5);
        assertThat(linhas.get(0)).isEqualTo("pessoa@escritorio.test | Enviou o PDF");
        assertThat(linhas.get(1)).startsWith("pessoa@escritorio.test | Calculou o prazo: data limite");
        assertThat(linhas.get(2)).startsWith("pessoa@escritorio.test | Gerou o link para o cliente");
        assertThat(linhas.get(3)).isEqualTo(Historico.CLIENTE + " | Confirmou a ciência marcando "
                + Datas.DIA.format(aviso.getPrazo()));
        assertThat(linhas.get(4)).isEqualTo("pessoa@escritorio.test | Resolveu o caso. Desfecho: Arquivado");
        //E a tela do caso mostra o cartão com os eventos
        String pagina = escritorioNavegando.perform(get(endereco)).andReturn().getResponse().getContentAsString();
        assertThat(pagina).contains("Histórico", "Enviou o PDF", "Resolveu o caso. Desfecho: Arquivado");
        assertThat(pagina.indexOf("Enviou o PDF")).isLessThan(pagina.indexOf("Resolveu o caso"));
    }

    @Test
    void historicoDeOutroEscritorioNaoAparece() {
        //Arrumar: um caso do escritorio vizinho, com um evento
        Escritorio vizinho = escritorios.save(new Escritorio("Outro Escritório"));
        Notificacao doVizinho = notificacoes.save(new Notificacao(
                empresas.save(new Empresa(vizinho, "99999009000281", "Empresa do Vizinho")), new byte[0], "Texto.",
                null, List.of(), java.util.List.of("Motivo de teste.")));
        historico.registrar(doVizinho, "alguem@vizinho.test", "Enviou o PDF");

        //Agir e conferir: buscado com o escritorio de teste, o historico vem vazio
        assertThat(historico.doCaso(doVizinho.getId(), escritorio.getId())).isEmpty();
        assertThat(historico.doCaso(doVizinho.getId(), vizinho.getId())).hasSize(1);
    }
}
