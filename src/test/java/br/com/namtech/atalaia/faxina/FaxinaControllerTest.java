package br.com.namtech.atalaia.faxina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//A faxina de sexta: casos abertos sem movimento há mais de 7 dias, o vencido primeiro, e os botões que anotam
@SpringBootTest
@Transactional
class FaxinaControllerTest {

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

    //Para mandar ao banco o que está na mesa e contar os eventos de um caso
    @Autowired
    private BancoDeTeste banco;

    //A data de chegada e a dos eventos são postas pelo sistema na hora; o teste as volta no tempo direto no banco
    @Autowired
    private JdbcTemplate sql;

    private MockMvc navegador;
    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    @Test
    void mostraSoOsCasosAbertosParadosComOVencidoPrimeiro() throws Exception {
        //Arrumar: quatro casos abertos e um resolvido, todos chegados há 20 dias
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        Notificacao semPrazo = casoDe("Padaria Sem Prazo", null);
        Notificacao correndo = casoDe("Oficina Correndo", hoje.plusDays(10));
        Notificacao vencido = casoDe("Loja Vencida", hoje.minusDays(2));
        Notificacao mexidoOntem = casoDe("Mercado Mexido", null);
        Notificacao resolvido = casoDe("Farmácia Resolvida", null);
        resolvido.resolver(Desfecho.ARQUIVADO, "Arquivado pelo órgão.");
        voltaNoTempo(semPrazo, 20);
        voltaNoTempo(correndo, 20);
        voltaNoTempo(vencido, 20);
        voltaNoTempo(mexidoOntem, 20);
        voltaNoTempo(resolvido, 20);
        //Alguém mexeu ontem no Mercado: ele tem o envio de 20 dias atrás e um evento de ontem. O mais novo conta
        historico.registrar(mexidoOntem, "pessoa@escritorio.test", "Calculou o prazo");
        banco.gravar();
        sql.update("update evento_do_caso set quando = ? where notificacao_id = ? and acao = 'Calculou o prazo'",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), mexidoOntem.getId());
        //Um caso de outro escritório, também parado há 20 dias, não pode aparecer
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        Notificacao alheio = notificacoes.save(new Notificacao(empresas.save(new Empresa(outro, "99999009004999",
                "Empresa Alheia Parada")), "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        historico.registrar(alheio, null, "Enviou o PDF");
        voltaNoTempo(alheio, 20);

        //Agir
        String pagina = navegador.perform(get("/faxina")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        //Conferir: os três parados, nesta ordem (vencido, com prazo, sem prazo), com os dias parados.
        //O mexido ontem e o resolvido não aparecem
        assertThat(pagina).contains("3 casos parados", "Parado há <span class=\"num\">20</span> dias")
                .doesNotContain("Mercado Mexido", "Farmácia Resolvida", "Empresa Alheia Parada");
        int loja = pagina.indexOf("<strong>Loja Vencida");
        int oficina = pagina.indexOf("<strong>Oficina Correndo");
        int padaria = pagina.indexOf("<strong>Padaria Sem Prazo");
        assertThat(loja).isPositive().isLessThan(oficina);
        assertThat(oficina).isLessThan(padaria);
        assertThat(pagina).contains("etq num atencao\">Venceu em ", "etq falta\">Sem prazo calculado<");
        //"Resolvido" leva ao cartão de fechar o caso, que pede desfecho e observação
        assertThat(pagina).contains("href=\"/notificacoes/" + vencido.getId() + "?fechar=1#fechar\"");
    }

    @Test
    void casoComMovimentoRecenteNaoEntraNaFaxina() throws Exception {
        //Arrumar: um caso que chegou hoje
        casoDe("Padaria Nova", null);

        //Agir e conferir: nada parado, e a tela diz isso
        navegador.perform(get("/faxina"))
                .andExpect(content().string(containsString("Nenhum caso parado.")))
                .andExpect(content().string(not(containsString("Padaria Nova"))));
    }

    @Test
    void seguirAbertoOuAdiarAnotaNoHistoricoETiraDaFaxinaPorUmaSemana() throws Exception {
        //Arrumar: dois casos parados há 10 dias
        Notificacao segue = casoDe("Padaria Que Segue", null);
        Notificacao adia = casoDe("Oficina Que Adia", null);
        voltaNoTempo(segue, 10);
        voltaNoTempo(adia, 10);

        //Agir: um botão em cada caso
        navegador.perform(post("/faxina/" + segue.getId()).param("acao", "segue"))
                .andExpect(redirectedUrl("/faxina"))
                .andExpect(flash().attribute("anotado", "Anotado no histórico do caso de Padaria Que Segue."));
        navegador.perform(post("/faxina/" + adia.getId()).param("acao", "adiar"))
                .andExpect(redirectedUrl("/faxina"));

        //Conferir: cada caso ganhou a sua anotação depois do envio, com quem fez, e a faxina ficou vazia
        assertThat(historico.doCaso(segue.getId(), escritorio.getId()))
                .extracting(evento -> evento.getAcao() + " / " + evento.getQuem())
                .containsExactly("Enviou o PDF / pessoa@escritorio.test",
                        FaxinaController.SEGUE_ABERTO + " / pessoa@escritorio.test");
        assertThat(historico.doCaso(adia.getId(), escritorio.getId()))
                .extracting(evento -> evento.getAcao()).containsExactly("Enviou o PDF", FaxinaController.ADIOU);
        navegador.perform(get("/faxina")).andExpect(content().string(containsString("Nenhum caso parado.")));
    }

    @Test
    void casoResolvidoNoMeioTempoNaoGanhaAnotacao() throws Exception {
        //Arrumar: a faxina ficou aberta numa aba, e outra pessoa fechou o caso
        Notificacao caso = casoDe("Padaria Fechada", null);
        caso.resolver(Desfecho.ARQUIVADO, "Fechado por outra pessoa.");

        //Agir: o clique da aba velha chega
        navegador.perform(post("/faxina/" + caso.getId()).param("acao", "segue"))
                .andExpect(redirectedUrl("/faxina"))
                .andExpect(flash().attribute("anotado", "O caso de Padaria Fechada já foi fechado. Nada foi anotado."));

        //Conferir: só o envio está no histórico
        assertThat(banco.contarEventosDo(caso.getId())).isEqualTo(1);
    }

    @Test
    void cliqueRepetidoNaoGravaDuasVezes() throws Exception {
        //Arrumar: um caso parado há 10 dias
        Notificacao caso = casoDe("Padaria Do Clique Duplo", null);
        voltaNoTempo(caso, 10);

        //Agir: o mesmo botão duas vezes
        navegador.perform(post("/faxina/" + caso.getId()).param("acao", "segue"));
        navegador.perform(post("/faxina/" + caso.getId()).param("acao", "segue"))
                .andExpect(flash().attribute("anotado", "O caso de Padaria Do Clique Duplo já teve movimento nesta semana."));

        //Conferir: o envio e uma anotação só
        assertThat(banco.contarEventosDo(caso.getId())).isEqualTo(2);
    }

    @Test
    void semPrazoOParadoHaMaisTempoVemPrimeiro() throws Exception {
        //Arrumar: dois casos sem prazo, um parado há 10 dias e outro há 20
        voltaNoTempo(casoDe("Padaria Dez Dias", null), 10);
        voltaNoTempo(casoDe("Oficina Vinte Dias", null), 20);

        //Agir
        String pagina = navegador.perform(get("/faxina")).andReturn().getResponse().getContentAsString();

        //Conferir: o de 20 dias em cima
        assertThat(pagina.indexOf("<strong>Oficina Vinte Dias")).isPositive()
                .isLessThan(pagina.indexOf("<strong>Padaria Dez Dias"));
    }

    @Test
    void acaoInventadaECasoDeOutroEscritorioSaoRecusados() throws Exception {
        //Arrumar: um caso meu e um de outro escritório
        Notificacao meu = casoDe("Padaria Minha", null);
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        Notificacao alheio = notificacoes.save(new Notificacao(empresas.save(new Empresa(outro, "99999009004009",
                "Empresa Alheia")), "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));

        //Agir e conferir: ação que não existe (formulário mexido) é 400; caso de outro escritório é 404, como se não existisse
        navegador.perform(post("/faxina/" + meu.getId()).param("acao", "apagar")).andExpect(status().isBadRequest());
        navegador.perform(post("/faxina/" + alheio.getId()).param("acao", "segue")).andExpect(status().isNotFound());
        assertThat(banco.contarEventosDo(meu.getId())).isEqualTo(1);
        assertThat(banco.contarEventosDo(alheio.getId())).isZero();
    }

    private int cnpjs = 0;

    //Um caso aberto da empresa dada, com a data limite dada (null = sem prazo ainda)
    private Notificacao casoDe(String nomeDaEmpresa, LocalDate dataLimite) {
        String cnpj = "9999900900" + String.format("%04d", 4000 + cnpjs++);
        Notificacao caso = notificacoes.save(new Notificacao(empresas.save(new Empresa(escritorio, cnpj, nomeDaEmpresa)),
                "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of()));
        //Como no envio de verdade, todo caso nasce com o evento "Enviou o PDF"
        historico.registrar(caso, "pessoa@escritorio.test", "Enviou o PDF");
        if (dataLimite != null) {
            caso.registrarPrazo(dataLimite.minusDays(15), dataLimite, List.of("Conta de teste."), "pessoa@escritorio.test");
        }
        return caso;
    }

    //Faz o caso parecer chegado há tantos dias: a chegada e todos os eventos dele voltam no tempo
    private void voltaNoTempo(Notificacao caso, int dias) {
        banco.gravar();
        Timestamp antes = Timestamp.from(Instant.now().minus(dias, ChronoUnit.DAYS));
        sql.update("update notificacao set recebida_em = ? where id = ?", antes, caso.getId());
        sql.update("update evento_do_caso set quando = ? where notificacao_id = ?", antes, caso.getId());
    }
}
