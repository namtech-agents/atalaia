package br.com.namtech.atalaia.prazo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Só regra inventada: "Prazo de teste" e "Norma de Teste, art. 1" nao existem em lei nenhuma.
//As datas de ciência ficam em 2026, perto de hoje, porque a tela recusa ciência a mais de dois anos de distancia
@SpringBootTest
@Transactional
class PrazoControllerTest {

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

    private MockMvc navegador;
    private Escritorio escritorio;
    private Notificacao notificacao;

    @BeforeEach
    void criaOEscritorioComUmaNotificacaoEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        notificacao = notificacaoDo(escritorio);
    }

    private Notificacao notificacaoDo(Escritorio dono) {
        Empresa empresa = empresas.save(new Empresa(dono, "99999009000100", "Empresa de Teste"));
        return notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto de teste.",
                TipoDeDocumento.INTIMACAO, List.of(), List.of()));
    }

    private RegraCadastrada regraDo(Escritorio dono, int dias) {
        return regras.save(new RegraCadastrada(dono, new RegraDePrazo("Prazo de teste", dias, TipoDeContagem.DIAS_UTEIS,
                false, "Norma de Teste, art. 1", LocalDate.now(Notificacao.TERESINA))));
    }

    private String endereco(Notificacao qual) {
        return "/notificacoes/" + qual.getId() + "/prazo";
    }

    @Test
    void calculaOPrazoEMostraADataComAConta() throws Exception {
        //Arrumar: regra de 5 dias uteis; ciência numa segunda, 02/03/2026
        RegraCadastrada regra = regraDo(escritorio, 5);

        //Agir
        navegador.perform(post(endereco(notificacao)).param("regraId", regra.getId().toString())
                .param("ciencia", "2026-03-02"))
                .andExpect(redirectedUrl("/notificacoes/" + notificacao.getId() + "#titulo-prazo"));

        //Conferir: a contagem começa na terça (03/03) e o quinto dia util é a segunda seguinte (09/03)
        assertThat(notificacao.getDataLimite()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(notificacao.getCiencia()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(notificacao.getPrazoCalculadoPor()).isEqualTo("pessoa@escritorio.test");
        //A tela mostra a data, a regra com o artigo e o aviso dos feriados que nao entram
        navegador.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("09/03/2026")))
                .andExpect(content().string(containsString("Norma de Teste, art. 1")))
                .andExpect(content().string(containsString("Data limite: 09/03/2026.")))
                .andExpect(content().string(containsString("Feriado estadual, municipal")))
                .andExpect(content().string(containsString("Refazer a conta")));
    }

    @Test
    void aContaPulaFeriadoNacional() throws Exception {
        //Arrumar: regra de 2 dias uteis; ciência na sexta, 17/04/2026
        RegraCadastrada regra = regraDo(escritorio, 2);

        //Agir
        navegador.perform(post(endereco(notificacao)).param("regraId", regra.getId().toString())
                .param("ciencia", "2026-04-17"));

        //Conferir: segunda 20/04 conta, terça 21/04 é Tiradentes, o segundo dia util é a quarta 22/04
        assertThat(notificacao.getDataLimite()).isEqualTo(LocalDate.of(2026, 4, 22));
    }

    @Test
    void semCienciaOuSemRegraNaoCalculaEAvisa() throws Exception {
        //Arrumar
        RegraCadastrada regra = regraDo(escritorio, 5);

        //Agir e conferir: ciência que nao é data, ciência com o ano errado e regra faltando
        for (String ciencia : new String[] { "", "ontem", "2206-03-02" }) {
            navegador.perform(post(endereco(notificacao)).param("regraId", regra.getId().toString())
                    .param("ciencia", ciencia))
                    .andExpect(flash().attribute("erroDoPrazo", containsString("Informe o dia da ciência")));
        }
        navegador.perform(post(endereco(notificacao)).param("ciencia", "2026-03-02"))
                .andExpect(flash().attribute("erroDoPrazo", "Escolha a regra de prazo."));

        //Conferir: nada foi gravado
        assertThat(notificacao.getCiencia()).isNull();
        assertThat(notificacao.getDataLimite()).isNull();
    }

    @Test
    void regraDeOutroEscritorioNaoServeParaCalcular() throws Exception {
        //Arrumar: a regra é do vizinho
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        RegraCadastrada alheia = regraDo(outro, 5);

        //Agir e conferir: para quem pede, a regra nao existe
        navegador.perform(post(endereco(notificacao)).param("regraId", alheia.getId().toString())
                .param("ciencia", "2026-03-02"))
                .andExpect(flash().attribute("erroDoPrazo", "Escolha a regra de prazo."));
        assertThat(notificacao.getDataLimite()).isNull();
    }

    @Test
    void naoDaParaCalcularOPrazoDaNotificacaoDeOutroEscritorio() throws Exception {
        //Arrumar: a notificação é do vizinho; a regra é de quem entrou
        Notificacao alheia = notificacaoDo(escritorios.save(new Escritorio("Outro Escritório")));
        RegraCadastrada regra = regraDo(escritorio, 5);

        //Agir e conferir: responde como se a notificação nao existisse
        navegador.perform(post(endereco(alheia)).param("regraId", regra.getId().toString())
                .param("ciencia", "2026-03-02"))
                .andExpect(status().isNotFound());
        assertThat(alheia.getDataLimite()).isNull();
    }

    @Test
    void casoResolvidoNaoTemAContaRefeita() throws Exception {
        //Arrumar: prazo calculado e caso fechado
        RegraCadastrada regra = regraDo(escritorio, 5);
        navegador.perform(post(endereco(notificacao)).param("regraId", regra.getId().toString())
                .param("ciencia", "2026-03-02"));
        notificacao.resolver(Desfecho.ARQUIVADO, "Respondido no prazo.");

        //Agir: tenta refazer com outra ciência
        navegador.perform(post(endereco(notificacao)).param("regraId", regra.getId().toString())
                .param("ciencia", "2026-03-10"));

        //Conferir: a conta que valeu continua la, e a tela nao oferece refazer
        assertThat(notificacao.getDataLimite()).isEqualTo(LocalDate.of(2026, 3, 9));
        navegador.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("09/03/2026")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Refazer a conta"))));
    }

    @Test
    void semRegraCadastradaATelaApontaParaOCadastro() throws Exception {
        //Agir e conferir
        navegador.perform(get("/notificacoes/" + notificacao.getId()))
                .andExpect(content().string(containsString("Ainda sem prazo")))
                .andExpect(content().string(containsString("Cadastrar a primeira regra de prazo")));
    }
}
