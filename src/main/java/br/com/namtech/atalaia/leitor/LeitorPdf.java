package br.com.namtech.atalaia.leitor;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class LeitorPdf {

    //Notificação é um documento de poucas paginas. Acima disso é engano ou abuso, e ler tudo custaria memoria e tempo
    public static final int MAXIMO_DE_PAGINAS = 100;
    private String extrairTexto(byte[] pdf) throws IOException {
        // Abre o PDF; o try fecha o documento sozinho no fim
        //createTempFileOnlyStreamCache: o que o PDFBox descompacta vai para um arquivo temporario, nao para a memoria.
        //Um PDF pequeno pode "inchar" muito ao ser aberto; assim ele nao derruba o servidor
        try (PDDocument documento = Loader.loadPDF(pdf, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            if (documento.getNumberOfPages() > MAXIMO_DE_PAGINAS) {
                throw new PaginasDemais();
            }
            PDFTextStripper extrator = new PDFTextStripper();
            return extrator.getText(documento);
        }
    }

    public ResultadoLeitura ler(byte[] pdf) throws IOException {
        String texto = extrairTexto(pdf);
        //Pdf so de imagem volta em branco (so espaços e quebras de linhas)
        boolean precisaDeRevisao = texto.isBlank();
        return new ResultadoLeitura(texto, precisaDeRevisao);
    }
}
