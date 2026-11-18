package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Sobe a aplicação inteira com o banco de teste. MockMvc: faz os pedidos como um navegador faria, sem abrir um
//Transactional: cada teste desfaz o que gravou no fim, para um nao atrapalhar o outro
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecepcaoControllerTest {

    @Autowired
    private MockMvc navegador;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    private Empresa empresa;

    @BeforeEach
    void criaUmaEmpresaInventada() {
        empresa = empresas.save(new Empresa("99999009000100", "Empresa de Teste"));
    }

    private MockMultipartFile pdfDeExemplo(String nome) throws IOException {
        byte[] bytes = getClass().getResourceAsStream("/pdfs/" + nome).readAllBytes();
        return new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_PDF_VALUE, bytes);
    }

    @Test
    void telaDeEnvioMostraAsEmpresasParaEscolher() throws Exception {
        //Agir e conferir: a tela abre e a empresa aparece na lista
        navegador.perform(get("/enviar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Empresa de Teste · CNPJ 99999009000100")));
    }

    @Test
    void pdfEnviadoELidoEGuardadoComOOriginal() throws Exception {
        //Arrumar: o PDF de exemplo com acento
        MockMultipartFile pdf = pdfDeExemplo("notificacao-acentuada.pdf");

        //Agir: envia escolhendo a empresa
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: a notificação ficou guardada com o texto lido, o PDF original e sem pedir revisao
        Notificacao guardada = notificacoes.findAll().getLast();
        assertThat(guardada.getTexto()).contains("Prazo: 30 dias a contar da ciência");
        assertThat(guardada.getArquivo()).isEqualTo(pdf.getBytes());
        assertThat(guardada.isPrecisaDeRevisao()).isFalse();
        assertThat(guardada.getDataLimite()).isNull();

        //Conferir: a pagina da notificação abre com a empresa e o texto, e o PDF original é entregue igual
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Empresa de Teste")))
                .andExpect(content().string(containsString("Texto lido")));
        navegador.perform(get("/notificacoes/" + guardada.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf.getBytes()));
    }

    @Test
    void pdfEscaneadoFicaMarcadoParaRevisao() throws Exception {
        //Arrumar: o PDF que é só imagem
        MockMultipartFile pdf = pdfDeExemplo("notificacao-escaneada.pdf");

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: ficou guardada pedindo revisao, e a pagina avisa
        Notificacao guardada = notificacoes.findAll().getLast();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Precisa de gente")));
    }

    @Test
    void arquivoQueNaoEPdfERecusadoComMensagem() throws Exception {
        //Arrumar: um arquivo de texto com nome e tipo de PDF (o disfarce nao pode enganar)
        MockMultipartFile falso = new MockMultipartFile("arquivo", "notificacao.pdf",
                MediaType.APPLICATION_PDF_VALUE, "isto nao é um PDF".getBytes());
        long antes = notificacoes.count();

        //Agir e conferir: volta para o formulario com a mensagem, e nada é guardado
        navegador.perform(multipart("/enviar").file(falso).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este arquivo não é um PDF.")));
        assertThat(notificacoes.count()).isEqualTo(antes);
    }

    @Test
    void arquivoGrandeDemaisERecusadoComMensagem() throws Exception {
        //Arrumar: um arquivo com 1 byte a mais que o limite
        byte[] grande = new byte[(int) RecepcaoController.TAMANHO_MAXIMO + 1];
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "grande.pdf", MediaType.APPLICATION_PDF_VALUE, grande);

        //Agir e conferir: recusado com a mensagem do limite
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("O limite é 10 MB.")));
    }

    @Test
    void pdfCorrompidoERecusadoComMensagem() throws Exception {
        //Arrumar: começa como PDF, mas o resto é lixo
        MockMultipartFile quebrado = new MockMultipartFile("arquivo", "quebrado.pdf",
                MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 lixo".getBytes());

        //Agir e conferir: recusado com mensagem, sem tela de erro
        navegador.perform(multipart("/enviar").file(quebrado).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Não foi possível abrir este PDF.")));
    }

    @Test
    void notificacaoQueNaoExisteDa404() throws Exception {
        navegador.perform(get("/notificacoes/999999")).andExpect(status().isNotFound());
    }
}
