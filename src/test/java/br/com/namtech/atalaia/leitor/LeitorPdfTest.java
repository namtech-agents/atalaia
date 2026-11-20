package br.com.namtech.atalaia.leitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDSimpleFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionRemoteGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.action.PDPageAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;

public class LeitorPdfTest {

    @Test 
    void devolveOTextoDeUmPdfNormal() throws IOException {
        //Arrumar: Abre o PDF de exemplo e pega os bytes dele
        InputStream arquivo = getClass().getResourceAsStream("/pdfs/notificacao-simples.pdf");
        byte[] pdf = arquivo.readAllBytes();

        //Agir: pede o texto ao leitor
        LeitorPdf leitor = new LeitorPdf();
        String texto = leitor.ler(pdf).texto();


        //Conferir: a frase do prazo tem que estar no texto 
        assertThat(texto).contains("Prazo: 30 dias a contar da ciencia.");
    }

    @Test
    void mandaPdfSoDeImagemParaRevisao() throws IOException {

    // Arrumar: abre o PDF que é só imagem e pega os bytes dele
    InputStream arquivo = getClass().getResourceAsStream("/pdfs/notificacao-escaneada.pdf");
    byte[] pdf = arquivo.readAllBytes();

    //Agir: pede a leitura completa ao leitor
    LeitorPdf leitor = new LeitorPdf();
    ResultadoLeitura resultado = leitor.ler(pdf);

    //Conferir: sem texto legível, tem que ir para revisão humana
    assertThat(resultado.precisaDeRevisao()).isTrue();
    }

    @Test
    void mantemOsAcentosDoTexto() throws IOException {
        //Arrumar: abre o PDF com acento e pega os bytes dele
        InputStream arquivo = getClass().getResourceAsStream("/pdfs/notificacao-acentuada.pdf");
        byte[] pdf = arquivo.readAllBytes();

        //Agir: pede a leitura completa ao leitor
        LeitorPdf leitor = new LeitorPdf();
        ResultadoLeitura resultado = leitor.ler(pdf);

        //Conferir: os acentos saem inteiros e nao precisa de revisao
        assertThat(resultado.texto()).contains("Notificação de lançamento");
        assertThat(resultado.texto()).contains("Prazo: 30 dias a contar da ciência");
        assertThat(resultado.precisaDeRevisao()).isFalse();
    }

    //Monta na hora um PDF em branco com uma pagina a mais que o limite.
    //Com "mentindo", o arquivo declara ter uma pagina só (o campo /Count), como faria quem quer burlar o teto
    public static byte[] pdfComPaginasDemais(boolean mentindo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PDDocument documento = new PDDocument()) {
            for (int pagina = 0; pagina <= LeitorPdf.MAXIMO_DE_PAGINAS; pagina++) {
                documento.addPage(new PDPage());
            }
            if (mentindo) {
                documento.getPages().getCOSObject().setInt(COSName.COUNT, 1);
            }
            documento.save(saida);
        }
        return saida.toByteArray();
    }

    @Test
    void recusaPdfComPaginasDemais() throws IOException {
        //Arrumar: um PDF com 101 paginas
        byte[] pdf = pdfComPaginasDemais(false);

        //Agir e conferir: o leitor recusa antes de tentar ler o texto
        LeitorPdf leitor = new LeitorPdf();
        assertThatThrownBy(() -> leitor.ler(pdf)).isInstanceOf(PdfRecusado.class).hasMessageContaining("páginas demais");
    }

    @Test
    void recusaPdfQueMenteONumeroDePaginas() throws IOException {
        //Arrumar: as mesmas 101 paginas, mas o arquivo diz que tem uma
        byte[] pdf = pdfComPaginasDemais(true);

        //Agir e conferir: o leitor conta de verdade e recusa do mesmo jeito
        LeitorPdf leitor = new LeitorPdf();
        assertThatThrownBy(() -> leitor.ler(pdf)).isInstanceOf(PdfRecusado.class).hasMessageContaining("páginas demais");
    }

    //A "bomba" de 19/11: um arquivo de cerca de 1 MB cujo conteudo, descomprimido, é um unico texto de 1 GB.
    //O PDFBox descomprime a parte inteira antes de ler, entao ela estoura qualquer memoria pequena.
    //Montar leva uns segundos: fica guardada para os testes que a usam
    private static byte[] bomba;

    public static synchronized byte[] pdfBomba() throws IOException {
        if (bomba != null) {
            return bomba;
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            COSStream conteudo = documento.getDocument().createCOSStream();
            //Grava ja comprimido (FlateDecode), do jeito que viria num arquivo de verdade
            try (OutputStream bruto = conteudo.createRawOutputStream();
                    DeflaterOutputStream comprimido = new DeflaterOutputStream(bruto)) {
                comprimido.write("BT (".getBytes());
                byte[] bloco = "a".repeat(1024 * 1024).getBytes();
                for (int mega = 0; mega < 1024; mega++) {
                    comprimido.write(bloco);
                }
                comprimido.write(") Tj ET".getBytes());
            }
            conteudo.setItem(COSName.FILTER, COSName.FLATE_DECODE);
            pagina.getCOSObject().setItem(COSName.CONTENTS, conteudo);
            documento.save(saida);
        }
        bomba = saida.toByteArray();
        return bomba;
    }

    @Test
    void recusaPdfQueIncha() throws IOException {
        //Arrumar: o arquivo pequeno que vira 1 GB quando aberto
        byte[] pdf = pdfBomba();
        assertThat(pdf.length).isLessThan(2 * 1024 * 1024);

        //Agir e conferir: o processo que le estoura a memoria dele (256 MB) e morre; o Atalaia so recusa
        LeitorPdf leitor = new LeitorPdf();
        assertThatThrownBy(() -> leitor.ler(pdf)).isInstanceOf(PdfRecusado.class).hasMessageContaining("pesado demais");
    }

    @Test
    void recusaPdfComLetrasDemais() throws IOException {
        //Arrumar: uma pagina com uma letra a mais que o teto, num arquivo de poucos KB
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream escrita = new PDPageContentStream(documento, pagina)) {
                escrita.beginText();
                escrita.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 1);
                escrita.showText("a".repeat(LeitorIsolado.MAXIMO_DE_LETRAS + 1));
                escrita.endText();
            }
            documento.save(saida);
        }

        //Agir e conferir: o leitor para de contar no teto e recusa.
        //Tempo folgado (2 min) de propósito: aqui o assunto é o teto de letras, não o relógio. Com o tempo normal
        //(20 s), numa máquina ocupada a contagem das 300 mil letras passava do tempo e quem respondia era o limite
        //de tempo, com outra mensagem (#206). O limite de tempo tem teste próprio, logo abaixo
        LeitorPdf leitor = new LeitorPdf(Duration.ofMinutes(2));
        assertThatThrownBy(() -> leitor.ler(saida.toByteArray()))
                .isInstanceOf(PdfRecusado.class).hasMessageContaining("pesado demais");
    }

    @Test
    void recusaLeituraQuePassaDoTempo() throws IOException {
        //Arrumar: um PDF normal, e um leitor cujo tempo ja acabou antes de começar
        byte[] pdf = getClass().getResourceAsStream("/pdfs/notificacao-simples.pdf").readAllBytes();
        LeitorPdf semTempo = new LeitorPdf(Duration.ZERO);

        //Agir e conferir: o leitor nao espera nada pelo processo que le, encerra o processo e desiste
        assertThatThrownBy(() -> semTempo.ler(pdf)).isInstanceOf(PdfRecusado.class).hasMessageContaining("demorou demais");
    }

    //Monta na hora um PDF com texto e com seis jeitos de o arquivo agir sozinho: script ao abrir o arquivo, no indice
    //de scripts do documento e ao abrir a pagina; links que abrem um programa, rodam script ou abrem arquivo da rede.
    //Tem tambem um link comum (endereço de site), que precisa continuar
    public static byte[] pdfComScripts() throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream escrita = new PDPageContentStream(documento, pagina)) {
                escrita.beginText();
                escrita.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                escrita.newLineAtOffset(72, 700);
                escrita.showText("Prazo: 30 dias a contar da ciencia.");
                escrita.endText();
            }
            documento.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('ao abrir')"));

            PDDocumentNameDictionary nomes = new PDDocumentNameDictionary(documento.getDocumentCatalog());
            PDJavascriptNameTreeNode scripts = new PDJavascriptNameTreeNode();
            scripts.setNames(Map.of("inicio", new PDActionJavaScript("app.alert('indice')")));
            nomes.setJavascript(scripts);
            documento.getDocumentCatalog().setNames(nomes);

            PDPageAdditionalActions daPagina = new PDPageAdditionalActions();
            daPagina.setO(new PDActionJavaScript("app.alert('pagina')"));
            pagina.setActions(daPagina);

            PDAnnotationLink abrePrograma = new PDAnnotationLink();
            abrePrograma.setRectangle(new PDRectangle(72, 600, 100, 20));
            PDActionLaunch lancar = new PDActionLaunch();
            lancar.setFile(new PDSimpleFileSpecification(new COSString("calc.exe")));
            abrePrograma.setAction(lancar);
            PDAnnotationLink site = new PDAnnotationLink();
            site.setRectangle(new PDRectangle(72, 560, 100, 20));
            PDActionURI endereco = new PDActionURI();
            endereco.setURI("https://www.gov.br");
            site.setAction(endereco);
            PDAnnotationLink scriptNoEndereco = new PDAnnotationLink();
            scriptNoEndereco.setRectangle(new PDRectangle(72, 520, 100, 20));
            PDActionURI javascript = new PDActionURI();
            javascript.setURI("  JavaScript:alert(document.domain)");
            scriptNoEndereco.setAction(javascript);
            //GoToR para um caminho de rede: no Windows, abrir faz o computador mandar a senha embaralhada para o servidor
            PDAnnotationLink arquivoDaRede = new PDAnnotationLink();
            arquivoDaRede.setRectangle(new PDRectangle(72, 480, 100, 20));
            PDActionRemoteGoTo outroArquivo = new PDActionRemoteGoTo();
            outroArquivo.setFile(new PDSimpleFileSpecification(new COSString("\\\\servidor\\x.pdf")));
            arquivoDaRede.setAction(outroArquivo);
            pagina.getAnnotations().add(abrePrograma);
            pagina.getAnnotations().add(site);
            pagina.getAnnotations().add(scriptNoEndereco);
            pagina.getAnnotations().add(arquivoDaRede);
            documento.save(saida);
        }
        return saida.toByteArray();
    }

    @Test
    void pdfComScriptsGanhaUmaCopiaSemEles() throws IOException {
        //Arrumar: o PDF com os seis jeitos de agir sozinho
        byte[] pdf = pdfComScripts();

        //Agir
        ResultadoLeitura resultado = new LeitorPdf().ler(pdf);

        //Conferir: o texto foi lido normalmente e veio uma copia
        assertThat(resultado.texto()).contains("Prazo: 30 dias a contar da ciencia.");
        assertThat(resultado.semScripts()).isNotNull();

        //Conferir: na copia nao sobra nenhum dos seis, o link do site continua e o texto é o mesmo
        try (PDDocument copia = Loader.loadPDF(resultado.semScripts())) {
            COSDictionary catalogo = copia.getDocumentCatalog().getCOSObject();
            assertThat(catalogo.containsKey(COSName.OPEN_ACTION)).isFalse();
            assertThat(copia.getDocumentCatalog().getNames().getJavaScript()).isNull();
            PDPage pagina = copia.getPage(0);
            assertThat(pagina.getCOSObject().containsKey(COSName.AA)).isFalse();
            assertThat(((PDAnnotationLink) pagina.getAnnotations().get(0)).getAction()).isNull();
            assertThat(((PDAnnotationLink) pagina.getAnnotations().get(1)).getAction()).isInstanceOf(PDActionURI.class);
            assertThat(((PDAnnotationLink) pagina.getAnnotations().get(2)).getAction()).isNull();
            assertThat(((PDAnnotationLink) pagina.getAnnotations().get(3)).getAction()).isNull();
            assertThat(new PDFTextStripper().getText(copia))
                    .contains("Prazo: 30 dias a contar da ciencia.");
        }
    }

    @Test
    void pdfSemScriptsNaoGanhaCopia() throws IOException {
        //Arrumar: o PDF de exemplo, sem nenhum script
        byte[] pdf = getClass().getResourceAsStream("/pdfs/notificacao-simples.pdf").readAllBytes();

        //Agir e conferir: nao ha o que tirar, entao a tela vai mostrar o proprio original
        assertThat(new LeitorPdf().ler(pdf).semScripts()).isNull();
    }
}
