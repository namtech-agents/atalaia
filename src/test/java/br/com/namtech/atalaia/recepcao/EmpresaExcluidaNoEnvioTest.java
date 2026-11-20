package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;

//Um colega exclui a empresa enquanto a IA lê o PDF que outra pessoa enviou para ela (revisão do Hefesto, #183).
//Sem Transactional, como o ResolverAoMesmoTempoTest: a exclusão e o envio precisam gravar de verdade, cada um
//na sua conversa com o banco. Por isso o teste apaga no fim o que criou
@SpringBootTest
class EmpresaExcluidaNoEnvioTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private BancoDeTeste banco;

    @MockitoBean
    private ModeloDeIA ia;

    private Escritorio escritorio;

    @AfterEach
    void apagaOQueCriou() {
        if (escritorio != null) {
            banco.apagar(Escritorio.class, escritorio.getId());
        }
    }

    @Test
    void envioParaEmpresaExcluidaDuranteALeituraVoltaComMensagem() throws Exception {
        //Arrumar: uma empresa sem casos, e uma IA que, enquanto "pensa", vê o colega excluir a empresa
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        when(ia.responder(anyString())).thenAnswer(pergunta -> {
            empresas.excluirSemCasos(empresa.getId(), escritorio.getId());
            return "{\"tipo\": \"INFORMATIVO\", \"confianca\": \"alta\", \"dados\": []}";
        });
        byte[] pdf = getClass().getResourceAsStream("/pdfs/notificacao-acentuada.pdf").readAllBytes();
        long antes = banco.contar(Notificacao.class);

        //Agir e conferir: o envio volta para o formulário explicando o que houve, sem página de erro
        NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()))
                .perform(multipart("/enviar")
                        .file(new MockMultipartFile("arquivo", "n.pdf", MediaType.APPLICATION_PDF_VALUE, pdf))
                        .param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Esta empresa foi excluída enquanto o PDF era lido.")));
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
    }
}
