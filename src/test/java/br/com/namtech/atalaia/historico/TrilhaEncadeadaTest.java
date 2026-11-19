package br.com.namtech.atalaia.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import jakarta.persistence.EntityManager;

//A trilha encadeada (US27): cada evento assina junto a assinatura do anterior, e mexer num registro antigo
//aparece na conferência, na tela e no PDF
@SpringBootTest
@Transactional
class TrilhaEncadeadaTest {

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

    //JdbcTemplate: mexe no banco por fora do Atalaia, como faria alguém com acesso direto à tabela
    @Autowired
    private JdbcTemplate sql;

    @Autowired
    private EntityManager mesa;

    private Escritorio escritorio;
    private Notificacao caso;
    private MockMvc navegador;

    @BeforeEach
    void criaUmCasoComTresEventos() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        caso = notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "99999009005000", "Padaria da Trilha")),
                "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
    }

    private void tresEventos() {
        historico.registrar(caso, "pessoa@escritorio.test", "Enviou o PDF");
        historico.registrar(caso, "pessoa@escritorio.test", "Calculou o prazo: data limite 10/12/2026 (ciência 20/11/2026)");
        historico.registrar(caso, Historico.CLIENTE, "Confirmou a ciência marcando 10/12/2026");
    }

    //Manda tudo para o banco e esquece o que está na memória: a próxima leitura vem do banco, com o que mudou nele
    private List<EventoDoCaso> relidos() {
        mesa.flush();
        mesa.clear();
        return historico.doCaso(caso.getId(), escritorio.getId());
    }

    @Test
    void cadaEventoNovoLevaAAssinaturaEncadeadaNaAnterior() {
        //Agir
        tresEventos();
        List<EventoDoCaso> eventos = relidos();

        //Conferir: três assinaturas de 64 letras (SHA-256 em hexadecimal), todas diferentes, e cada uma refeita
        //com a anterior confere. O selo é a assinatura do último
        assertThat(eventos).extracting(EventoDoCaso::getAssinatura).allMatch(assinatura -> assinatura.matches("[0-9a-f]{64}"))
                .doesNotHaveDuplicates();
        Trilha trilha = Trilha.conferir(eventos);
        assertThat(trilha.integra()).isTrue();
        assertThat(trilha.selo()).isEqualTo(eventos.get(2).getAssinatura());
        assertThat(eventos.get(1).confere(eventos.get(0).getAssinatura())).isTrue();
        //A mesma frase com outra assinatura anterior não confere: a corrente amarra a ordem
        assertThat(eventos.get(1).confere("")).isFalse();
    }

    @Test
    void alterarUmRegistroAntigoQuebraATrilhaDaliEmDiante() throws Exception {
        //Arrumar: três eventos gravados
        tresEventos();
        mesa.flush();

        //Agir: alguém com acesso ao banco troca a data limite do segundo registro
        sql.update("update evento_do_caso set acao = ? where notificacao_id = ? and acao like 'Calculou%'",
                "Calculou o prazo: data limite 20/12/2026 (ciência 20/11/2026)", caso.getId());
        List<EventoDoCaso> eventos = relidos();

        //Conferir: a conferência acusa a quebra exatamente no registro alterado
        Trilha trilha = Trilha.conferir(eventos);
        assertThat(trilha.integra()).isFalse();
        assertThat(trilha.quebra().getAcao()).contains("20/12/2026");
        assertThat(trilha.selo()).isNull();
        //A tela do caso avisa, em vermelho, e não diz que conferiu
        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(containsString("A trilha não confere a partir do registro de")))
                .andExpect(content().string(not(containsString("Trilha conferida"))));
    }

    @Test
    void mudarAHoraOuQuemFezTambemQuebra() {
        //Cada campo entra na assinatura: a hora e quem fez também
        for (String adulteracao : new String[] {
                "update evento_do_caso set quando = ? where notificacao_id = ? and acao = 'Enviou o PDF'",
                "update evento_do_caso set quem = 'outra@pessoa.test' where ? is not null and notificacao_id = ? and acao = 'Enviou o PDF'" }) {
            //Arrumar: um caso novo com três eventos
            caso = notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, "9999900900" + (6000 + empresasCriadas++),
                    "Padaria Adulterada")), "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
            tresEventos();
            mesa.flush();

            //Agir: a adulteração (o SQL recebe um instante e o número do caso)
            sql.update(adulteracao, Timestamp.from(Instant.parse("2026-11-01T12:00:00Z")), caso.getId());

            //Conferir: quebra no primeiro registro
            Trilha trilha = Trilha.conferir(relidos());
            assertThat(trilha.integra()).as(adulteracao).isFalse();
            assertThat(trilha.quebra().getAcao()).isEqualTo("Enviou o PDF");
        }
    }

    private int empresasCriadas = 0;

    @Test
    void apagarUmRegistroDoMeioTambemQuebra() {
        //Arrumar e agir: três eventos, e alguém apaga o do meio
        tresEventos();
        mesa.flush();
        sql.update("delete from evento_do_caso where notificacao_id = ? and acao like 'Calculou%'", caso.getId());

        //Conferir: o terceiro foi assinado com a assinatura do segundo, que sumiu
        Trilha trilha = Trilha.conferir(relidos());
        assertThat(trilha.integra()).isFalse();
        assertThat(trilha.quebra().getAcao()).startsWith("Confirmou");
    }

    @Test
    void apagarAAssinaturaDeUmRegistroQuebra() {
        //Arrumar e agir: alguém tira a assinatura do segundo registro, para ele parecer "de antes da trilha"
        tresEventos();
        mesa.flush();
        sql.update("update evento_do_caso set assinatura = null where notificacao_id = ? and acao like 'Calculou%'",
                caso.getId());

        //Conferir: depois do primeiro registro assinado, registro sem assinatura é quebra
        Trilha trilha = Trilha.conferir(relidos());
        assertThat(trilha.integra()).isFalse();
        assertThat(trilha.quebra().getAcao()).startsWith("Calculou");
    }

    @Test
    void registrosDeAntesDaTrilhaFicamForaDaContaEOsNovosComecamDoZero() {
        //Arrumar: um registro antigo, sem assinatura, como os que a V11 criou
        mesa.flush();
        sql.update("insert into evento_do_caso (notificacao_id, quando, quem, acao) values (?, ?, null, 'Enviou o PDF')",
                caso.getId(), Timestamp.from(Instant.parse("2026-11-01T12:00:00Z")));

        //Agir: um registro novo
        historico.registrar(caso, "pessoa@escritorio.test", "Resolveu o caso. Desfecho: Arquivado");

        //Conferir: a trilha começa no registro novo, encadeado no vazio, e confere
        List<EventoDoCaso> eventos = relidos();
        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).getAssinatura()).isNull();
        Trilha trilha = Trilha.conferir(eventos);
        assertThat(trilha.integra()).isTrue();
        assertThat(trilha.temAssinatura()).isTrue();
        assertThat(eventos.get(1).confere("")).isTrue();
    }

    @Test
    void casoSoComRegistrosAntigosNaoDizQueConferiu() throws Exception {
        //Arrumar: só um registro antigo
        mesa.flush();
        sql.update("insert into evento_do_caso (notificacao_id, quando, quem, acao) values (?, ?, null, 'Enviou o PDF')",
                caso.getId(), Timestamp.from(Instant.parse("2026-11-01T12:00:00Z")));
        mesa.clear();

        //Agir e conferir: nada quebrado, mas também nada para afirmar
        Trilha trilha = Trilha.conferir(historico.doCaso(caso.getId(), escritorio.getId()));
        assertThat(trilha.integra()).isTrue();
        assertThat(trilha.temAssinatura()).isFalse();
        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este caso não tem registros assinados")))
                .andExpect(content().string(not(containsString("Trilha conferida"))))
                .andExpect(content().string(not(containsString("A trilha não confere"))));
    }

    @Test
    void telaDizQueConferiuEOPdfTrazCadaAssinaturaEOSelo() throws Exception {
        //Arrumar: três eventos, um deles com letra que a fonte do PDF não tem (aspas curvas e emoji)
        tresEventos();
        historico.registrar(caso, "pessoa@escritorio.test", "Reabriu o caso. Observação: “cliente ligou” 📞");
        List<EventoDoCaso> eventos = relidos();
        String selo = eventos.get(3).getAssinatura();

        //Agir e conferir: a tela diz que conferiu e oferece o PDF
        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(containsString("Trilha conferida: nenhum registro foi alterado")))
                .andExpect(content().string(containsString("/notificacoes/" + caso.getId() + "/trilha")));

        //Agir: baixa o PDF
        byte[] pdf = navegador.perform(get("/notificacoes/" + caso.getId() + "/trilha"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        //Conferir: o texto do PDF traz a empresa, a conferência, cada assinatura e o selo; a letra fora da fonte virou "?"
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            //As aspas curvas a fonte escreve; o emoji, não
            assertThat(texto).contains("Trilha do caso nº " + caso.getId(), "Padaria da Trilha",
                    "todas as assinaturas conferem", "Selo da trilha", "“cliente ligou” ?");
            //A assinatura de 64 letras pode quebrar de linha no PDF: confere sem as quebras
            assertThat(texto.replaceAll("\\s", "")).contains(selo, eventos.get(0).getAssinatura());
        }
    }

    @Test
    void pdfDaTrilhaDeOutroEscritorioNaoExiste() throws Exception {
        //Arrumar: um caso de outro escritório, com evento
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        Notificacao alheio = notificacoes.save(new Notificacao(empresas.save(new Empresa(outro, "99999009005001", "Alheia")),
                "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        historico.registrar(alheio, "alguem@outro.test", "Enviou o PDF");

        //Agir e conferir: responde como se não existisse
        navegador.perform(get("/notificacoes/" + alheio.getId() + "/trilha")).andExpect(status().isNotFound());
    }
}
