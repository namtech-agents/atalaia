package br.com.namtech.atalaia.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//US23: nenhuma empresa ve os dados de outra. Dois escritórios; a pessoa de um tenta, por todos os caminhos,
//chegar na notificação e na empresa do outro
@SpringBootTest
@Transactional
class IsolamentoEntreEscritoriosTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    @MockitoBean
    private ModeloDeIA ia;

    //O navegador da pessoa de fora (do outro escritório)
    private MockMvc deFora;

    private Escritorio dono;
    private Empresa empresaAlheia;
    private Notificacao notificacaoAlheia;

    @BeforeEach
    void criaDoisEscritorios() {
        //O escritório dono dos dados, com uma empresa e uma notificação
        dono = escritorios.save(new Escritorio("Escritório Dono"));
        empresaAlheia = empresas.save(new Empresa(dono, "99999009000100", "Empresa Secreta"));
        notificacaoAlheia = notificacoes.save(new Notificacao(empresaAlheia, "%PDF-teste".getBytes(), "texto secreto",
                TipoDeDocumento.COBRANCA, List.of(), List.of()));

        //O outro escritório, de onde vem a pessoa que tenta ver
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        deFora = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(outro.getId()));
    }

    @Test
    void notificacaoDeOutroEscritorioRespondeComoSeNaoExistisse() throws Exception {
        //Agir e conferir: a pagina e o PDF dao "não encontrado", igual a um numero que nao existe
        deFora.perform(get("/notificacoes/" + notificacaoAlheia.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Empresa Secreta"))));
        deFora.perform(get("/notificacoes/" + notificacaoAlheia.getId() + "/pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDaParaResolverANotificacaoDeOutroEscritorio() throws Exception {
        //Agir: manda resolver direto pelo endereço
        deFora.perform(post("/notificacoes/" + notificacaoAlheia.getId() + "/resolver")
                .param("desfecho", "ARQUIVADO").param("observacao", "Tentativa de fora."))
                .andExpect(status().isNotFound());

        //Conferir: o caso continua aberto
        assertThat(notificacaoAlheia.isResolvida()).isFalse();
    }

    @Test
    void listasNaoMostramNadaDeOutroEscritorio() throws Exception {
        //Agir e conferir: nem a tela inicial, nem a de empresas, nem a lista de escolha do envio mostram a empresa alheia
        for (String endereco : new String[] {"/", "/empresas", "/enviar"}) {
            deFora.perform(get(endereco))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Empresa Secreta"))));
        }

        //Conferir: o dono enxerga (sem isso, o teste passaria até se a tela nao mostrasse empresa nenhuma)
        NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(dono.getId()))
                .perform(get("/empresas"))
                .andExpect(content().string(containsString("Empresa Secreta")));
    }

    @Test
    void naoDaParaEnviarPdfParaAEmpresaDeOutroEscritorio() throws Exception {
        //Arrumar: um PDF de verdade (com um PDF quebrado o envio seria recusado de qualquer jeito,
        //e o teste passaria mesmo sem a proteção), e a contagem de antes
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "teste.pdf", MediaType.APPLICATION_PDF_VALUE,
                getClass().getResourceAsStream("/pdfs/notificacao-simples.pdf").readAllBytes());
        long antes = notificacoes.count();

        //Agir: envia trocando, no formulario, o numero da empresa pelo da empresa alheia
        deFora.perform(multipart("/enviar").file(pdf).param("empresaId", empresaAlheia.getId().toString()))
                .andExpect(status().isBadRequest());

        //Conferir: nada foi guardado
        assertThat(notificacoes.count()).isEqualTo(antes);
    }
}
