package br.com.namtech.atalaia.leitor;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class LeitorPdf {
    public String extrairTexto(byte[] pdf) throws IOException {
        // Abre o PDF; o try fecha o documento sozinho no fim
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extrator = new PDFTextStripper();
            return extrator.getText(documento);
        }
    }
}
