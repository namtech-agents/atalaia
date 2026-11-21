package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.analista.Degrau;
import br.com.namtech.atalaia.analista.IaDeTeste;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.leitor.LeitorPdfTest;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.seguranca.LimiteDeTentativas;
import br.com.namtech.atalaia.verificador.DadoExtraido;
import jakarta.persistence.EntityManager;

//Sobe a aplicação inteira com o banco de teste. MockMvc: faz os pedidos como um navegador faria, sem abrir um
//Transactional: cada teste desfaz o que gravou no fim, para um nao atrapalhar o outro
@SpringBootTest
@Transactional
class RecepcaoControllerTest {

    //O navegador de mentira é montado antes de cada teste, ja com a sessão de uma pessoa do escritório de teste
    private MockMvc navegador;

    //Para conferir o banco inteiro (os repositórios só buscam dentro de um escritório)
    @Autowired
    private BancoDeTeste banco;

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

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

    //A conta de envios por escritório, a mesma que o RecepcaoController usa
    @Autowired
    @Qualifier("limiteDeEnvios")
    private LimiteDeTentativas limiteDeEnvios;

    private Empresa empresa;

    @BeforeEach
    void criaUmEscritorioComUmaEmpresaInventadaEEntra() {
        Escritorio escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        empresa = empresas.save(new Empresa(escritorio, "99999009000100", "Empresa de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    //Sem outra ordem, a IA de mentira responde certo para o PDF de exemplo: um dado que esta mesmo no texto
    @BeforeEach
    void iaRespondeCerto() {
        iaResponde("30 dias");
    }

    private void iaResponde(String prazo) {
        IaDeTeste.responde(ia, """
                {"tipo": "INTIMACAO", "confianca": "alta", "degrau": "PEDIDO_DE_DOCUMENTO", "dados": [
                  {"campo": "prazo", "valor": "%s", "trecho": "Prazo: 30 dias a contar da ciência"}],
                 "resumo": "A Receita pediu os livros da empresa. Separe os livros e avise o escritório."}
                """.formatted(prazo));
    }

    //Manda para o banco o que esta na mesa e limpa a mesa: a proxima busca le do banco de verdade,
    //em vez de devolver o mesmo objeto que o controller acabou de salvar
    private Notificacao ultimaGuardadaNoBanco() {
        mesa.flush();
        mesa.clear();
        return banco.ultima(Notificacao.class);
    }

    private MockMultipartFile pdfDeExemplo(String nome) throws IOException {
        byte[] bytes = getClass().getResourceAsStream("/pdfs/" + nome).readAllBytes();
        return new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_PDF_VALUE, bytes);
    }

    @Test
    void telaDeEnvioSemEmpresaMostraOCaminhoSemFormulario() throws Exception {
        //Arrumar: um escritório que ainda nao cadastrou empresa
        Escritorio novo = escritorios.save(new Escritorio("Escritório Recém-criado"));

        //Agir e conferir: aparece o aviso e o link para cadastrar, e nenhum formulário de envio
        NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(novo.getId()))
                .perform(get("/enviar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma empresa cadastrada ainda.")))
                .andExpect(content().string(containsString("href=\"/empresas\"")))
                .andExpect(content().string(not(containsString("multipart/form-data"))))
                .andExpect(content().string(not(containsString("botao-enviar"))));
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
        //O degrau sugerido pela IA (US28)
        assertThat(guardada.getDegrau()).isEqualTo(Degrau.PEDIDO_DE_DOCUMENTO);

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
                .andExpect(content().string(containsString("Texto lido")))
                .andExpect(content().string(not(containsString("Este PDF trazia scripts."))));
        navegador.perform(get("/notificacoes/" + guardada.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                //o PDF sai sem a CSP das paginas, mas com os outros cabeçalhos de segurança
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(pdf.getBytes()));
    }

    @Test
    void resumoSugeridoPelaIaAbreOCampoDoAvisoComoRascunho() throws Exception {
        //Arrumar: o PDF enviado, e depois a data limite calculada (sem ela a tela nao mostra o formulario do aviso)
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.getResumoSugerido())
                .isEqualTo("A Receita pediu os livros da empresa. Separe os livros e avise o escritório.");
        guardada.registrarPrazo(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), List.of("Conta de teste."),
                "pessoa@escritorio.test");

        //Agir e conferir: o campo do resumo abre com o rascunho, e a tela diz quem escreveu
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString(
                        ">A Receita pediu os livros da empresa. Separe os livros e avise o escritório.</textarea>")))
                .andExpect(content().string(containsString("Rascunho escrito por inteligência artificial")))
                //o leitor de tela anuncia a nota junto com o campo
                .andExpect(content().string(containsString("aria-describedby=\"rascunho-ia dica-resumo\"")));

        //O envio falhou e a tela voltou com o que a pessoa digitou: o rascunho nao passa por cima
        navegador.perform(get("/notificacoes/" + guardada.getId()).flashAttr("resumoDigitado", "Texto da pessoa."))
                .andExpect(content().string(containsString(">Texto da pessoa.</textarea>")))
                .andExpect(content().string(not(containsString("Rascunho escrito por inteligência artificial"))));

        //Depois que ja existe um aviso, o campo do aviso novo abre vazio: o rascunho era para o primeiro
        navegador.perform(post("/notificacoes/" + guardada.getId() + "/aviso").param("resumo", "Resumo aprovado."));
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Fazer um aviso novo")))
                .andExpect(content().string(not(containsString("Rascunho escrito por inteligência artificial"))));
    }

    @Test
    void documentoQueVaiParaRevisaoNaoGuardaResumoSugerido() throws Exception {
        //Arrumar: a IA inventa um prazo que nao esta no documento (o Verificador manda para revisao)
        iaResponde("45 dias");

        //Agir
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));

        //Conferir: se os dados nao valem, o resumo feito em cima deles tambem nao
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        assertThat(guardada.getResumoSugerido()).isNull();
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
                .andExpect(content().string(containsString("Conferir a leitura")))
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
                .andExpect(content().string(containsString("Por que conferir a leitura")))
                .andExpect(content().string(containsString("não aparece no trecho")))
                //o dado inventado nao chega na tela, nem a frase de conferido
                .andExpect(content().string(not(containsString("45 dias"))))
                .andExpect(content().string(not(containsString("conferido com o texto do PDF"))));
    }

    @Test
    void informativoQueFalaEmPrazoVaiParaRevisao() throws Exception {
        //Arrumar: a IA chama de informativo um documento que tem prazo (engano, ou documento feito para enganar)
        IaDeTeste.responde(ia, """
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
    void duvidaDaSegundaLeituraMandaOCasoParaGenteComOTrechoEOPdfAoLado() throws Exception {
        //Arrumar: a leitura passa em tudo, mas a segunda passada acha um conflito no prazo
        IaDeTeste.responde(ia, """
                {"tipo": "INTIMACAO", "confianca": "alta", "dados": [
                  {"campo": "prazo", "valor": "30 dias", "trecho": "Prazo: 30 dias a contar da ciência"}],
                 "resumo": "A Receita pediu os livros da empresa. Separe os livros e avise o escritório."}
                """, """
                {"duvida": true, "trecho": "Prazo: 30 dias a contar da ciência", "porque": "O prazo aparece de dois jeitos."}
                """);

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o caso foi para uma pessoa, com o motivo e o trecho; a tela mostra o motivo e o PDF ao lado
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isPrecisaDeRevisao()).isTrue();
        //Caso em revisão não leva o rascunho do aviso ao cliente
        assertThat(guardada.getResumoSugerido()).isNull();
        assertThat(guardada.getMotivosDaRevisao()).containsExactly("A segunda leitura achou algo faltando ou em conflito."
                + " Explicação da IA, não conferida: O prazo aparece de dois jeitos."
                + " Trecho: \"Prazo: 30 dias a contar da ciência\"");
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conferir a leitura")))
                .andExpect(content().string(containsString("O prazo aparece de dois jeitos.")))
                .andExpect(content().string(containsString("/notificacoes/" + guardada.getId() + "/pdf")));
    }

    @Test
    void casoQueJaFoiParaGenteNaoGastaASegundaLeitura() throws Exception {
        //Arrumar: a leitura já pede revisão (a IA ficou em dúvida sobre o tipo)
        IaDeTeste.responde(ia, "{\"tipo\": \"INTIMACAO\", \"confianca\": \"baixa\", \"dados\": []}");

        //Agir: envia
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: uma pergunta só à IA (a leitura); a pessoa vai olhar o documento de qualquer jeito
        verify(ia, times(1)).responder(anyString());
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
    void casoQueAIaNaoLeuPodeSerLidoDeNovo() throws Exception {
        //Arrumar: na chegada a IA estava fora do ar, e o caso ficou sem tipo, pedindo revisão (#216)
        when(ia.responder(anyString())).thenThrow(new IllegalStateException("fora do ar"));
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));
        Notificacao semTipo = ultimaGuardadaNoBanco();
        assertThat(semTipo.getTipo()).isNull();
        navegador.perform(get("/notificacoes/" + semTipo.getId()))
                .andExpect(content().string(containsString("Tentar ler de novo")))
                .andExpect(content().string(containsString("Escolha o tipo")));

        //Agir: a IA voltou, e a pessoa pede outra leitura
        reset(ia);
        iaResponde("30 dias");
        navegador.perform(post("/notificacoes/" + semTipo.getId() + "/ler-de-novo"))
                .andExpect(redirectedUrl("/notificacoes/" + semTipo.getId()));

        //Conferir: agora tem tipo, a leitura foi conferida e o pedido ficou no histórico
        Notificacao lida = ultimaGuardadaNoBanco();
        assertThat(lida.getTipo()).isEqualTo(TipoDeDocumento.INTIMACAO);
        assertThat(lida.isPrecisaDeRevisao()).isFalse();
        assertThat(lida.getDados()).isNotEmpty();
        navegador.perform(get("/notificacoes/" + lida.getId()))
                .andExpect(content().string(containsString("Pediu uma nova leitura do documento: leitura conferida")));

        //Um segundo clique (aba velha, clique duplo) não chama a IA de novo: o caso já tem tipo
        reset(ia);
        navegador.perform(post("/notificacoes/" + lida.getId() + "/ler-de-novo"));
        verify(ia, never()).responder(anyString());
        assertThat(ultimaGuardadaNoBanco().getTipo()).isEqualTo(TipoDeDocumento.INTIMACAO);
    }

    @Test
    void tipoEscolhidoAMaoNaoEApagadoPorOutraLeitura() throws Exception {
        //Arrumar: caso sem tipo, e a pessoa escolhe Cobrança (revisão do Hefesto, #216)
        when(ia.responder(anyString())).thenThrow(new IllegalStateException("fora do ar"));
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));
        Notificacao caso = ultimaGuardadaNoBanco();
        navegador.perform(post("/notificacoes/" + caso.getId() + "/tipo").param("tipo", "COBRANCA"));

        //Agir: alguém pede outra leitura (a IA continua fora do ar)
        navegador.perform(post("/notificacoes/" + caso.getId() + "/ler-de-novo"));

        //Conferir: a escolha da pessoa ficou, e o botão nem aparece mais
        assertThat(ultimaGuardadaNoBanco().getTipo()).isEqualTo(TipoDeDocumento.COBRANCA);
        navegador.perform(get("/notificacoes/" + caso.getId()))
                .andExpect(content().string(not(containsString("Tentar ler de novo"))));
    }

    @Test
    void tipoQueNaoExisteERecusado() throws Exception {
        when(ia.responder(anyString())).thenThrow(new IllegalStateException("fora do ar"));
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));
        Notificacao caso = ultimaGuardadaNoBanco();
        navegador.perform(post("/notificacoes/" + caso.getId() + "/tipo").param("tipo", "XYZ"))
                .andExpect(status().isBadRequest());
        assertThat(ultimaGuardadaNoBanco().getTipo()).isNull();
    }

    @Test
    void tipoEscolhidoAMaoVaiParaOCasoEParaOHistorico() throws Exception {
        //Arrumar: um caso sem tipo (IA fora do ar na chegada)
        when(ia.responder(anyString())).thenThrow(new IllegalStateException("fora do ar"));
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                .param("empresaId", empresa.getId().toString()));
        Notificacao semTipo = ultimaGuardadaNoBanco();

        //Agir: a pessoa escolhe o tipo
        navegador.perform(post("/notificacoes/" + semTipo.getId() + "/tipo").param("tipo", "COBRANCA"))
                .andExpect(redirectedUrl("/notificacoes/" + semTipo.getId()));

        //Conferir: o tipo ficou, a revisão continua (o tipo não prova a leitura) e o histórico conta quem mudou
        Notificacao corrigida = ultimaGuardadaNoBanco();
        assertThat(corrigida.getTipo()).isEqualTo(TipoDeDocumento.COBRANCA);
        assertThat(corrigida.isPrecisaDeRevisao()).isTrue();
        navegador.perform(get("/notificacoes/" + corrigida.getId()))
                .andExpect(content().string(containsString("Corrigiu o tipo do documento: de sem tipo para Cobrança")));
    }

    @Test
    void arquivoQueNaoEPdfERecusadoComMensagem() throws Exception {
        //Arrumar: um arquivo de texto com nome e tipo de PDF (o disfarce nao pode enganar)
        MockMultipartFile falso = new MockMultipartFile("arquivo", "notificacao.pdf",
                MediaType.APPLICATION_PDF_VALUE, "isto nao é um PDF".getBytes());
        long antes = banco.contar(Notificacao.class);

        //Agir e conferir: volta para o formulario com a mensagem, e nada é guardado
        navegador.perform(multipart("/enviar").file(falso).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este arquivo não é um PDF.")))
                //o campo do arquivo fica marcado como invalido e ligado à mensagem, para o leitor de tela
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("aria-describedby=\"erro-envio dica-arquivo\"")))
                //a empresa escolhida volta marcada, para a pessoa nao escolher de novo
                .andExpect(content().string(containsString("selected=\"selected\"")));
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
    }

    @Test
    void escritorioQueEnviouDemaisEmPoucoTempoEsperaSemGastarAIa() throws Exception {
        //Arrumar: o escritório já gastou todos os envios da janela (cada envio custa uma chamada à IA)
        while (limiteDeEnvios.reservar(String.valueOf(empresa.getEscritorio().getId()))) {
            //gasta a próxima vaga
        }
        long antes = banco.contar(Notificacao.class);

        //Agir e conferir: o envio seguinte é recusado com 429 (pedidos demais), a IA não é chamada e nada é guardado
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-acentuada.pdf"))
                        .param("empresaId", empresa.getId().toString()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                //o problema não é o arquivo: o campo dele não fica marcado como inválido
                .andExpect(content().string(not(containsString("aria-invalid"))))
                .andExpect(content().string(containsString("Muitos PDFs enviados em pouco tempo.")));
        verify(ia, never()).responder(anyString());
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
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
        long antes = banco.contar(Notificacao.class);
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este PDF tem páginas demais")));
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
    }

    @Test
    void pdfQueInchaERecusadoComMensagem() throws Exception {
        //Arrumar: o arquivo pequeno que vira 1 GB quando aberto
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "bomba.pdf", MediaType.APPLICATION_PDF_VALUE,
                LeitorPdfTest.pdfBomba());

        //Agir e conferir: recusa com a mensagem e nao guarda nada
        long antes = banco.contar(Notificacao.class);
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Este PDF é pesado demais para ser lido. Escolha outro arquivo.")));
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
    }

    @Test
    void pdfComScriptsGuardaOOriginalEMostraACopiaSemEles() throws Exception {
        //Arrumar: um PDF com scripts escondidos
        byte[] original = LeitorPdfTest.pdfComScripts();
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "scripts.pdf", MediaType.APPLICATION_PDF_VALUE, original);

        //Agir
        navegador.perform(multipart("/enviar").file(pdf).param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o original ficou guardado igual e existe uma copia diferente dele
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.getArquivo()).isEqualTo(original);
        assertThat(guardada.getArquivoSemScripts()).isNotNull().isNotEqualTo(original);

        //Conferir: a moldura recebe a copia, e a tela conta que é uma copia
        navegador.perform(get("/notificacoes/" + guardada.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(guardada.getArquivoSemScripts()));
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Este PDF trazia scripts.")));
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

    //Roda uma vez para cada desfecho da lista. O banco tem uma restrição com os mesmos nomes (V5):
    //quem criar um desfecho novo no Java e esquecer a migração ve este teste falhar, em vez de um erro no ar
    @ParameterizedTest
    @EnumSource(Desfecho.class)
    void bancoAceitaTodosOsDesfechosDaLista(Desfecho desfecho) throws Exception {
        //Arrumar: uma notificação aberta
        Notificacao aberta = notificacaoAberta();

        //Agir: marca como resolvida com o desfecho da vez
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", desfecho.name()).param("observacao", "Observação."))
                .andExpect(status().is3xxRedirection());

        //Conferir: o banco gravou (a busca manda a mudança para o banco, e a restrição é conferida ali)
        assertThat(ultimaGuardadaNoBanco().getDesfecho()).isEqualTo(desfecho);
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
    void reabrirVoltaOCasoParaAbertoEODesfechoAnteriorFicaNoHistorico() throws Exception {
        //Arrumar: um caso resolvido com o desfecho errado (a observação longa testa a coluna do historico)
        Notificacao aberta = notificacaoAberta();
        String observacaoLonga = "x".repeat(500);
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "ARQUIVADO").param("observacao", observacaoLonga))
                .andExpect(status().is3xxRedirection());

        //Agir: reabre, mandando o carimbo do fechamento que a tela mostrou
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/reabrir").param("fechadoEm", carimbo()))
                .andExpect(status().is3xxRedirection());

        //Conferir: o caso voltou a ficar aberto, sem desfecho, e o formulario de fechar aparece de novo.
        //O que estava registrado nao se perde: vai inteiro para o historico
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isResolvida()).isFalse();
        assertThat(guardada.getDesfecho()).isNull();
        assertThat(guardada.getObservacao()).isNull();
        navegador.perform(get("/notificacoes/" + guardada.getId()))
                .andExpect(content().string(containsString("Marcar como resolvida")))
                .andExpect(content().string(not(containsString("Reabrir o caso"))))
                .andExpect(content().string(containsString(
                        "Reabriu o caso. Desfecho anterior: Arquivado. Observação: " + observacaoLonga)))
                //e quem reabriu
                .andExpect(content().string(containsString("pessoa@escritorio.test")));

        //e o caso pode ser fechado de novo, com o desfecho certo
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/resolver")
                .param("desfecho", "MULTA_REDUZIDA").param("observacao", "Corrigido."))
                .andExpect(status().is3xxRedirection());
        assertThat(ultimaGuardadaNoBanco().getDesfecho()).isEqualTo(Desfecho.MULTA_REDUZIDA);
    }

    //O carimbo do fechamento atual, como o formulario de reabrir manda
    private String carimbo() {
        return String.valueOf(ultimaGuardadaNoBanco().getResolvidaEm().toEpochMilli());
    }

    @Test
    void abaVelhaNaoDesfazOFechamentoNovoDeOutraPessoa() throws Exception {
        //Arrumar: a pessoa A abriu a tela do caso fechado e guardou o carimbo que viu.
        //Nesse meio tempo, a pessoa B reabriu e fechou de novo, com o desfecho certo
        Notificacao aberta = notificacaoAberta();
        aberta.resolver(Desfecho.ARQUIVADO, "Primeiro fechamento.");
        String carimboQueAViu = String.valueOf(aberta.getResolvidaEm().minusMillis(1000).toEpochMilli());

        //Agir: A clica em reabrir com o carimbo velho (e alguem manda sem carimbo nenhum)
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/reabrir").param("fechadoEm", carimboQueAViu))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erroDoReabrir",
                        "O caso foi fechado de novo por outra pessoa. Confira antes de reabrir."));
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/reabrir"))
                .andExpect(status().is3xxRedirection());

        //Conferir: o fechamento de B continua valendo, e nada entrou no historico
        Notificacao guardada = ultimaGuardadaNoBanco();
        assertThat(guardada.isResolvida()).isTrue();
        assertThat(guardada.getObservacao()).isEqualTo("Primeiro fechamento.");
        navegador.perform(get("/notificacoes/" + aberta.getId()))
                .andExpect(content().string(not(containsString("Reabriu o caso"))));
    }

    @Test
    void casoReabertoVoltaParaATelaInicialEPodeAvisarOClienteDeNovo() throws Exception {
        //Arrumar: um caso com a data limite calculada e depois fechado
        Notificacao aberta = notificacaoAberta();
        aberta.registrarPrazo(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), List.of("Conta de teste."),
                "pessoa@escritorio.test");
        aberta.resolver(Desfecho.OUTRO, "Fechado cedo demais.");

        //Agir: reabre
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/reabrir").param("fechadoEm", carimbo()))
                .andExpect(redirectedUrl("/notificacoes/" + aberta.getId() + "?fechar=1#fechar"));

        //Conferir: a tela inicial lista o caso de novo entre os abertos, o filtro das resolvidas ficou sem ele
        //(cai nas abertas), e a tela do caso volta a oferecer o link do cliente
        String inicial = navegador.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(inicial).contains("/notificacoes/" + aberta.getId() + "\"");
        String filtro = navegador.perform(get("/").param("ver", "resolvidas"))
                .andReturn().getResponse().getContentAsString();
        //(sem nenhuma resolvida, a tela nem mostra o filtro: lista as abertas, com o caso de volta)
        assertThat(filtro).contains("/notificacoes/" + aberta.getId() + "\"", "Recebida em").doesNotContain("Desfecho: Outro");
        navegador.perform(get("/notificacoes/" + aberta.getId()))
                .andExpect(content().string(containsString("Gerar o link de aviso")));
        //e a conta do prazo pode ser refeita (com o caso fechado, registrarPrazo recusaria)
        //(relida do banco: o carimbo() acima desliga o objeto "aberta" da sessão, e ele ficou com o estado antigo)
        Notificacao reaberta = ultimaGuardadaNoBanco();
        reaberta.registrarPrazo(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 10), List.of("Refeita."), "pessoa@escritorio.test");
        assertThat(reaberta.getDataLimite()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void casoResolvidoMostraOBotaoDeReabrir() throws Exception {
        //Arrumar: um caso resolvido
        Notificacao aberta = notificacaoAberta();
        aberta.resolver(Desfecho.OUTRO, "Observação.");

        //Agir e conferir: a tela oferece reabrir, com o formulario que aponta para a rota certa
        navegador.perform(get("/notificacoes/" + aberta.getId()))
                .andExpect(content().string(containsString("Reabrir o caso")))
                .andExpect(content().string(containsString("/notificacoes/" + aberta.getId() + "/reabrir")))
                .andExpect(content().string(containsString(
                        "name=\"fechadoEm\" value=\"" + aberta.getResolvidaEm().toEpochMilli() + "\"")));
    }

    @Test
    void reabrirCasoQueJaEstaAbertoNaoMudaNada() throws Exception {
        //Arrumar: um caso aberto (clique duplo no reabrir, ou a pagina velha aberta em outra aba)
        Notificacao aberta = notificacaoAberta();

        //Agir: manda reabrir
        navegador.perform(post("/notificacoes/" + aberta.getId() + "/reabrir"))
                .andExpect(status().is3xxRedirection());

        //Conferir: continua aberto e nada entrou no historico
        assertThat(ultimaGuardadaNoBanco().isResolvida()).isFalse();
        navegador.perform(get("/notificacoes/" + aberta.getId()))
                .andExpect(content().string(not(containsString("Reabriu o caso"))));
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
        long antes = banco.contar(Notificacao.class);
        navegador.perform(multipart("/enviar").file(pdfDeExemplo("notificacao-simples.pdf")).param("empresaId", "999999"))
                .andExpect(status().isBadRequest());
        assertThat(banco.contar(Notificacao.class)).isEqualTo(antes);
    }

    @Test
    void notificacaoQueNaoExisteDa404() throws Exception {
        navegador.perform(get("/notificacoes/999999")).andExpect(status().isNotFound());
    }

    @Test
    void cnpjApareceComAPontuacao() {
        assertThat(new Empresa(null, "99999001000100", "Empresa de Teste").getCnpjFormatado())
                .isEqualTo("99.999.001/0001-00");
    }
}
