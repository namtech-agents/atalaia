package br.com.namtech.atalaia.variacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

//Uma notificação inventada para os testes de variação (US29), com as três coisas que dá para mudar uma de cada vez:
//a data do documento, a ordem dos parágrafos e o CNPJ. Todo documento sai com a marca de teste no topo.
//"with..." devolve uma cópia com só aquela mudança: a base fica igual, e cada variação difere dela em uma coisa
record DocumentoDeTeste(String data, String cnpj, boolean ordemTrocada) {

    static final String MARCA = "DOCUMENTO DE TESTE (INVENTADO) - sem valor legal";
    static final String TRECHO_DO_PRAZO = "Prazo: 20 dias a contar da ciência.";

    static DocumentoDeTeste base() {
        return new DocumentoDeTeste("03 de novembro de 2026", "99.999.001/0001-00", false);
    }

    DocumentoDeTeste comData(String outra) {
        return new DocumentoDeTeste(outra, cnpj, ordemTrocada);
    }

    DocumentoDeTeste comCnpj(String outro) {
        return new DocumentoDeTeste(data, outro, ordemTrocada);
    }

    DocumentoDeTeste comOrdemTrocada() {
        return new DocumentoDeTeste(data, cnpj, true);
    }

    //Os parágrafos do corpo, na ordem da base. Cada um é uma linha só, para a troca de ordem mexer em blocos inteiros
    List<String> paragrafos() {
        List<String> corpo = new ArrayList<>(List.of(
                "Contribuinte: Clínica Vida Mansa (exemplo), CNPJ " + cnpj,
                "Fica o contribuinte INTIMADO a apresentar os livros Caixa e Razão do ano de 2025.",
                "Apresentar também os extratos bancários do mesmo período.",
                TRECHO_DO_PRAZO,
                "Valor da multa em caso de descumprimento: R$ 1.250,00."));
        if (ordemTrocada) {
            //O contrário da base: o último vem primeiro
            Collections.reverse(corpo);
        }
        return corpo;
    }

    //O PDF de verdade, com texto (não imagem), para passar pelo mesmo Leitor que lê os PDFs enviados
    byte[] pdf() throws IOException {
        List<String> linhas = new ArrayList<>(List.of(MARCA, "", "RECEITA DE EXEMPLO - DELEGACIA DE FISCALIZAÇÃO",
                "Termo de Intimação Fiscal nº 2026.0042", "Teresina, " + data, ""));
        linhas.addAll(paragrafos());
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            try (PDPageContentStream tinta = new PDPageContentStream(documento, pagina)) {
                tinta.beginText();
                tinta.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                tinta.setLeading(16);
                tinta.newLineAtOffset(50, 780);
                for (String linha : linhas) {
                    tinta.showText(linha);
                    tinta.newLine();
                }
                tinta.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }
}
