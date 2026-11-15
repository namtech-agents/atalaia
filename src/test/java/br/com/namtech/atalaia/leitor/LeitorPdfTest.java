package br.com.namtech.atalaia.leitor;

import static org.assertj.core.api.Assertions.assertThat;

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
        String texto = leitor.extrairTexto(pdf);

        //Conferir: a frase do prazo tem que estar no texto 
        assertThat(texto).contains("Prazo: 30 dias a contar da ciencia.");
    }
}
