package br.com.namtech.atalaia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

//Um PDF simples de texto: título e linhas, em A4, com quebra de linha e de página. Serve à trilha do caso
//(TrilhaController) e ao documento do simulado (SimuladoController)
public final class PdfDeTexto {

    //Página A4, margem de 50 pontos (uns 18 mm) e corpo de 10 pontos
    private static final float MARGEM = 50;
    private static final float CORPO = 10;
    private static final float ENTRELINHA = 14;

    private PdfDeTexto() {
    }

    //Escreve o título e as linhas, quebrando cada uma pela largura da página e abrindo página nova quando acaba
    public static byte[] montar(String titulo, List<String> linhas) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;

            List<String> quebradas = new ArrayList<>();
            for (String linha : linhas) {
                quebradas.addAll(quebrar(semLetraForaDaFonte(linha, normal), normal, largura));
            }

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream tinta = new PDPageContentStream(documento, pagina);
            float y = PDRectangle.A4.getHeight() - MARGEM;
            escrever(tinta, negrito, 15, MARGEM, y, semLetraForaDaFonte(titulo, negrito));
            y -= 2 * ENTRELINHA;
            for (String linha : quebradas) {
                if (y < MARGEM) {
                    tinta.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    tinta = new PDPageContentStream(documento, pagina);
                    y = PDRectangle.A4.getHeight() - MARGEM;
                }
                escrever(tinta, normal, CORPO, MARGEM, y, linha);
                y -= ENTRELINHA;
            }
            tinta.close();
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private static void escrever(PDPageContentStream tinta, PDType1Font fonte, float tamanho, float x, float y,
            String texto) throws IOException {
        tinta.beginText();
        tinta.setFont(fonte, tamanho);
        tinta.newLineAtOffset(x, y);
        tinta.showText(texto);
        tinta.endText();
    }

    //A Helvetica padrão do PDF escreve as letras da tabela WinAnsi: acentos do português, aspas curvas, travessão
    //e euro inclusive. Letra fora dela (emoji, letra de outro alfabeto) derrubaria a montagem: vira "?".
    //Quebra de linha e tabulação dentro do texto viram espaço. A fonte responde letra por letra se sabe escrever
    private static String semLetraForaDaFonte(String texto, PDType1Font fonte) {
        StringBuilder limpo = new StringBuilder();
        texto.codePoints().forEach(letra -> {
            String umaLetra = Character.isISOControl(letra) ? " " : Character.toString(letra);
            try {
                fonte.encode(umaLetra);
                limpo.append(umaLetra);
            } catch (IOException | IllegalArgumentException foraDaFonte) {
                limpo.append('?');
            }
        });
        return limpo.toString();
    }

    //Quebra pelas palavras, medindo cada pedaço na fonte. Palavra maior que a linha (a assinatura de 64 letras
    //cabe; um texto sem espaço enorme não) é cortada em pedaços
    private static List<String> quebrar(String linha, PDType1Font fonte, float largura) throws IOException {
        List<String> saida = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : linha.split(" ", -1)) {
            String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (medir(tentativa, fonte) <= largura) {
                atual = new StringBuilder(tentativa);
                continue;
            }
            if (!atual.isEmpty()) {
                saida.add(atual.toString());
            }
            atual = new StringBuilder(palavra);
            while (medir(atual.toString(), fonte) > largura) {
                int corte = atual.length() - 1;
                while (corte > 1 && medir(atual.substring(0, corte), fonte) > largura) {
                    corte--;
                }
                saida.add(atual.substring(0, corte));
                atual = new StringBuilder(atual.substring(corte));
            }
        }
        saida.add(atual.toString());
        return saida;
    }

    private static float medir(String texto, PDType1Font fonte) throws IOException {
        return fonte.getStringWidth(texto) / 1000 * CORPO;
    }
}
