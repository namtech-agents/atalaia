package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.leitor.LeitorPdfTest;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.verificador.DadoExtraido;
import jakarta.persistence.EntityManager;

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

    //EntityManager: a "mesa de trabalho" do JPA, onde ficam os objetos ja lidos ou salvos neste teste
    @Autowired
    private EntityManager mesa;

    //MockitoBean: troca a IA de verdade por uma de mentira, que responde o que o teste mandar.
    //Assim os testes nao dependem de internet nem gastam a chave
    @MockitoBean
    private ModeloDeIA ia;

    private Empresa empresa;

    @BeforeEach
    void criaUmaEmpresaInventada() {
        empresa = empresas.save(new Empresa("99999009000100", "Empresa de Teste"));
    }

    //Sem outra ordem, a IA de mentira responde certo para o PDF de exemplo: um dado que esta mesmo no texto
    @BeforeEach
    void iaRespondeCerto() {
        iaResponde("30 dias");
    }

    private void iaResponde(String prazo) {
        when(ia.responder(anyString())).thenReturn("""
                {"tipo": "INTIMACAO", "confianca": "alta", "dados": [
                  {"campo": "prazo", "valor": "%s", "trecho": "Prazo: 30 dias a contar da ciência"}]}
                """.formatted(prazo));
    }

    //Manda para o banco o que esta na mesa e limpa a mesa: a proxima busca le do banco de verdade,
    //em vez de devolver o mesmo objeto que o controller acabou de salvar
    private Notificacao ultimaGuardadaNoBanco() {
        mesa.flush();
        mesa.clear();
        return notificacoes.findAll().getLast();
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
                .andExpect(content().string(containsString("Empresa de Teste · CNPJ 99.999.009/0001-00")));
    }

    @Test
    void pdfEnviadoELidoAnalisadoEGuardadoComOOriginal() throws Exception {
        //Arrumar: o PDF de exemplo com acento
        MockMultipartFile pdf = pdfDeExemplo("notificacao-acentuada.pdf");

        //Agir: envia escolhendo a empresa
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: a notificação ficou guardada com o texto lido, o PDF original e sem pedir revisao
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.getTexto()).contains("Prazo: 30 dias a contar da ciência");
        assertThat(guardada.getArquivo()).isEqualTo(pdf.getBytes());
        assertThat(guardada.isPrecisaDeRevisao()).isFalse();
        assertThat(guardada.getDataLimite()).isNull();

        //Conferir: guardou tambem o que o Analista entendeu, com o trecho de onde saiu o dado
        assertThat(guardada.getTipo()).isEqualTo(TipoDeDocumento.INTIMACAO);
        assertThat(guardada.getDados())
                .containsExactly(new DadoExtraido("prazo", "30 dias", "Prazo: 30 dias a contar da ciência"));
        assertThat(guardada.getMotivosDaRevisao()).isEmpty();

        //Conferir: a pagina da notificação abre com a empresa, o tipo, o dado e o texto, e o PDF original é entregue igual
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Empresa de Teste")))
                .andExpect(content().string(containsString("Intimação")))
                //(o nome do campo, ja com o rotulo da tela, so aparece no cartão dos dados; "30 dias" apareceria tambem no texto lido)
                .andExpect(content().string(containsString("<dt>Prazo</dt>")))
                .andExpect(content().string(containsString("conferido com o texto do PDF")))
                .andExpect(content().string(containsString("Texto lido")));
        navegador.perform(get("/notificacoes/" + guardada.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                //o PDF sai sem a CSP das paginas, mas com os outros cabeçalhos de segurança
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(pdf.getBytes()));
    }

    @Test
    void pdfEscaneadoFicaMarcadoParaRevisao() throws Exception {
        //Arrumar: o PDF que é só imagem
        MockMultipartFile pdf = pdfDeExemplo("notificacao-escaneada.pdf");

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: ficou guardada pedindo revisao, sem gastar chamada da IA, e a pagina avisa o motivo
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        verify(ia, never()).responder(anyString());
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Precisa de gente")))
                .andExpect(content().string(containsString("parece escaneado")))
                .andExpect(content().string(containsString("Abrir o PDF em outra aba")));
    }

    @Test
    void dadoQueNaoEstaNoPdfMandaParaRevisaoComOMotivo() throws Exception {
        //Arrumar: a IA inventa um prazo que nao esta no documento
        iaResponde("45 dias");

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o Verificador barrou, a notificação pede revisao e a pagina mostra por que
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        assertThat(guardada.getMotivosDaRevisao()).hasSize(1);
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Por que precisa de gente")))
                .andExpect(content().string(containsString("não aparece no trecho")))
                //o dado inventado nao chega na tela, nem a frase de conferido
                .andExpect(content().string(not(containsString("45 dias"))))
                .andExpect(content().string(not(containsString("conferido com o texto do PDF"))));
    }

    @Test
    void informativoQueFalaEmPrazoVaiParaRevisao() throws Exception {
        //Arrumar: a IA chama de informativo um documento que tem prazo (engano, ou documento feito para enganar)
        when(ia.responder(anyString())).thenReturn("""
                {"tipo": "INFORMATIVO", "confianca": "alta", "dados": [
                  {"campo": "prazo", "valor": "30 dias", "trecho": "Prazo: 30 dias a contar da ciência"}]}
                """);

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o codigo nao aceita o "informativo" calado: pede revisao com o motivo
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        assertThat(guardada.getMotivosDaRevisao())
                .containsExactly("A IA classificou como informativo, mas o texto fala em prazo, intimação, citação ou multa.");
    }

    @Test
    void iaForaDoArNaoImpedeOEnvio() throws Exception {
        //Arrumar: a IA da erro (sem internet, sem chave, limite estourado)
        when(ia.responder(anyString())).thenThrow(new IllegalStateException("fora do ar"));

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o PDF ficou guardado do mesmo jeito, sem tipo, pedindo revisao com o motivo
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.getTipo()).isNull();
        assertThat(guardada.getMotivosDaRevisao()).containsExactly("A IA não respondeu. O documento não foi analisado.");
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A IA não respondeu")));
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
                .andExpect(content().string(containsString("Este arquivo não é um PDF.")))
                //o campo do arquivo fica marcado como invalido e ligado à mensagem, para o leitor de tela
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("aria-describedby=\"erro-envio dica-arquivo\"")))
                //a empresa escolhida volta marcada, para a pessoa nao escolher de novo
                .andExpect(content().string(containsString("selected=\"selected\"")));
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
    void pdfComPaginasDemaisERecusadoComMensagem() throws Exception {
        //Arrumar: um PDF de 101 paginas que declara ter uma só
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "enorme.pdf", MediaType.APPLICATION_PDF_VALUE,
                LeitorPdfTest.pdfComPaginasDemais(true));

        //Agir e conferir: recusa com a mensagem e nao guarda nada
        long antes = notificacoes.count();
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este PDF tem páginas demais")));
        assertThat(notificacoes.count()).isEqualTo(antes);
    }

    //Guarda direto no banco uma notificação conferida, para os testes de resolver
    private Notificacao notificacaoAberta() {
        return notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "texto",
                TipoDeDocumento.COBRANCA, List.of(), List.of()));
    }

    @Test
    void resolverGuardaODesfechoEAObservacao() throws Exception {
        //Arrumar: uma notificação aberta
        Notificacao aberta = notificacaoAberta();

        //Agir: marca como resolvida
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "MULTA_REDUZIDA").param("observacao", "  Pago com desconto em 18/11.  "))
                .andExpect(status().is3xxRedirection());

        //Conferir: ficou guardado no banco (a observação sem os espaços das pontas) e a tela mostra, sem o formulario
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isResolvida()).isTrue();
        assertThat(guardada.getDesfecho()).isEqualTo(Desfecho.MULTA_REDUZIDA);
        assertThat(guardada.getObservacao()).isEqualTo("Pago com desconto em 18/11.");
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Multa reduzida")))
                .andExpect(content().string(containsString("Pago com desconto em 18/11.")))
                .andExpect(content().string(not(containsString("Marcar como resolvida"))));
    }

    @Test
    void resolverSemObservacaoERecusadoComMensagem() throws Exception {
        //Arrumar: uma notificação aberta
        Notificacao aberta = notificacaoAberta();

        //Agir e conferir: sem observação, volta a tela com a mensagem e o desfecho escolhido, e nada muda no banco
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "ARQUIVADO").param("observacao", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escreva o que foi feito")))
                .andExpect(content().string(containsString("<title>Erro · ")));
        //e sem desfecho a mensagem é a do desfecho
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver").param("observacao", "Pago."))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escolha o desfecho.")));
        assertThat(ultimaGuardadaNoBanco().isResolvida()).isFalse();
    }

    @Test
    void observacaoNoLimiteContaAQuebraDeLinhaComoUmaLetra() throws Exception {
        //Arrumar: 500 letras do jeito que a tela conta, com uma quebra de linha no meio (o navegador envia \r\n)
        Notificacao aberta = notificacaoAberta();
        String noLimite = "a".repeat(249) + "\r\n" + "b".repeat(250);

        //Agir e conferir: é aceita, e fica guardada com 500
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "OUTRO").param("observacao", noLimite))
                .andExpect(status().is3xxRedirection());
        assertThat(ultimaGuardadaNoBanco().getObservacao()).hasSize(500);
    }

    @Test
    void observacaoAcimaDoLimiteOuSoComEspacoInvisivelERecusada() throws Exception {
        //Arrumar: uma notificação aberta
        Notificacao aberta = notificacaoAberta();

        //Agir e conferir: 501 letras nao passa; e um espaço que nao quebra (colado do Word) nao conta como texto
        for (String observacao : List.of("a".repeat(501), "\u00A0\u200B")) {
            navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                    .param("desfecho", "OUTRO").param("observacao", observacao))
                    .andExpect(status().isBadRequest());
        }
        assertThat(ultimaGuardadaNoBanco().isResolvida()).isFalse();
    }

    @Test
    void resolverDeNovoNaoTrocaOQueFoiRegistrado() throws Exception {
        //Arrumar: uma notificação ja resolvida
        Notificacao aberta = notificacaoAberta();
        aberta.resolver(Desfecho.ARQUIVADO, "Primeira observação.");

        //Agir: alguem manda resolver de novo, com outro desfecho
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "MULTA_CHEIA").param("observacao", "Segunda observação."))
                .andExpect(status().is3xxRedirection());

        //Conferir: vale o primeiro registro
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.getDesfecho()).isEqualTo(Desfecho.ARQUIVADO);
        assertThat(guardada.getObservacao()).isEqualTo("Primeira observação.");
    }

    @Test
    void envioSemArquivoERecusadoComMensagem() throws Exception {
        //Arrumar: o campo do arquivo veio vazio
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        //Agir e conferir: pede para escolher um arquivo
        navegador.perform(multipart("/enviar").file(vazio).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escolha um arquivo PDF para enviar.")));
    }

    @Test
    void empresaQueNaoExisteERecusada() throws Exception {
        //Agir e conferir: um numero de empresa que nao esta no banco da 400 e nada é guardado
        long antes = notificacoes.count();
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-simples.pdf")).param("empresaId", "999999"))
                .andExpect(status().isBadRequest());
        assertThat(notificacoes.count()).isEqualTo(antes);
    }

    @Test
    void notificacaoQueNaoExisteDa404() throws Exception {
        navegador.perform(get("/notificacoes/999999")).andExpect(status().isNotFound());
    }

    @Test
    void cnpjApareceComAPontuacao() {
        assertThat(new Empresa("99999001000100", "Empresa de Teste").getCnpjFormatado())
                .isEqualTo("99.999.001/0001-00");
    }
}
