package br.com.namtech.atalaia.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Transactional: cada teste desfaz o que gravou no fim, para um nao atrapalhar o outro
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PainelControllerTest {

    @Autowired
    private MockMvc navegador;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    private static final byte[] PDF = "%PDF-teste".getBytes();

    @Test
    void painelVazioConvidaAEnviar() throws Exception {
        //Agir e conferir: sem nenhuma notificação, a tela explica o que fazer
        navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma notificação recebida ainda")));
    }

    @Test
    void painelSeparaOQuePrecisaDeGenteDoQueFoiConferido() throws Exception {
        //Arrumar: uma empresa inventada com uma notificação conferida e outra que foi para revisao
        Empresa empresa = empresas.save(new Empresa("99999009000100", "Empresa de Teste"));
        Notificacao conferida = notificacoes.save(
                new Notificacao(empresa, PDF, "texto", TipoDeDocumento.COBRANCA, List.of(), List.of()));
        Notificacao paraRevisao = notificacoes.save(
                new Notificacao(empresa, PDF, "", null, List.of(), List.of("Este PDF não tem texto.")));

        //Agir: abre a tela inicial
        String pagina = navegador.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        //Conferir: os dois grupos aparecem, "precisa de gente" primeiro, cada um com o link da sua notificação
        assertThat(pagina).contains("Empresa de Teste", "Cobrança", "Sem tipo");
        assertThat(pagina.indexOf("Precisa de gente")).isLessThan(pagina.indexOf("Lidas e conferidas"));
        assertThat(pagina.indexOf("/notificacoes/" + paraRevisao.getId()))
                .isBetween(pagina.indexOf("Precisa de gente"), pagina.indexOf("Lidas e conferidas"));
        assertThat(pagina.indexOf("/notificacoes/" + conferida.getId()))
                .isGreaterThan(pagina.indexOf("Lidas e conferidas"));
    }
}
