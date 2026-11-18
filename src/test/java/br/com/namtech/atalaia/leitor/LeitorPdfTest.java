package br.com.namtech.atalaia.leitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    @Test
    void recusaPdfComPaginasDemais() throws IOException {
        //Arrumar: monta na hora um PDF em branco com uma pagina a mais que o limite
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PDDocument documento = new PDDocument()) {
            for (int pagina = 0; pagina <= LeitorPdf.MAXIMO_DE_PAGINAS; pagina++) {
                documento.addPage(new PDPage());
            }
            documento.save(saida);
        }

        //Agir e conferir: o leitor recusa antes de tentar ler o texto
        LeitorPdf leitor = new LeitorPdf();
        assertThatThrownBy(() -> leitor.ler(saida.toByteArray())).isInstanceOf(PaginasDemais.class);
    }
}
