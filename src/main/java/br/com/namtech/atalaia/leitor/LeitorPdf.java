package br.com.namtech.atalaia.leitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

public class LeitorPdf {

    //Notificação é um documento de poucas paginas. Acima disso é engano ou abuso, e ler tudo custaria memoria e tempo
    public static final int MAXIMO_DE_PAGINAS = 100;

    //O PDF guarda o conteudo comprimido: um arquivo de 1 MB pode virar 1 GB ao abrir (a "bomba de descompressão").
    //Em 19/11, um PDF de 949 KB estourou a memoria e um de 1,4 MB prendeu a leitura por 20 s.
    //Uma notificação de 100 paginas com fontes embutidas fica bem abaixo de 50 MB (as imagens nao entram na conta)
    static final long CONTEUDO_MAXIMO = 50L * 1024 * 1024;

    //Uma notificação normal é lida em menos de 1 s. O teto pega o que é pequeno mas lento de propósito
    //(um desenho que se repete dentro dele mesmo milhares de vezes), que o teto de tamanho nao ve
    static final Duration TEMPO_MAXIMO = Duration.ofSeconds(15);

    private final Duration tempoMaximo;

    public LeitorPdf() {
        this(TEMPO_MAXIMO);
    }

    //Para os testes conseguirem um leitor sem tempo nenhum, sem precisar de um PDF que demore de verdade
    LeitorPdf(Duration tempoMaximo) {
        this.tempoMaximo = tempoMaximo;
    }

    public ResultadoLeitura ler(byte[] pdf) throws IOException {
        // Abre o PDF; o try fecha o documento sozinho no fim
        //createTempFileOnlyStreamCache: o que o PDFBox precisar guardar enquanto le vai para um arquivo temporario
        //(apagado no fim), em vez de ocupar a memoria do servidor
        try (PDDocument documento = Loader.loadPDF(pdf, "", null, null, IOUtils.createTempFileOnlyStreamCache())) {
            conferirPaginas(documento);
            conferirTamanho(documento.getDocument());
            String texto = new ExtratorComPrazo(tempoMaximo).getText(documento);
            //Pdf so de imagem volta em branco (so espaços e quebras de linhas)
            boolean precisaDeRevisao = texto.isBlank();
            return new ResultadoLeitura(texto, precisaDeRevisao, copiaSemScripts(documento));
        }
    }

    //Conta as paginas uma a uma. O numero que o PDF declara (getNumberOfPages) nao serve:
    //quem monta o arquivo escreve ali o que quiser
    private void conferirPaginas(PDDocument documento) throws PdfRecusado {
        int paginas = 0;
        for (PDPage pagina : documento.getPages()) {
            paginas++;
            if (paginas > MAXIMO_DE_PAGINAS) {
                throw new PdfRecusado("Este PDF tem páginas demais. O limite é " + MAXIMO_DE_PAGINAS + " páginas.");
            }
        }
    }

    //Descomprime cada parte do arquivo só para contar o tamanho, jogando fora o que leu, e para no teto.
    //Assim o texto gigante nunca chega a ser montado na memoria. As imagens ficam de fora: elas nao viram texto
    //e algumas (JPEG 2000) custariam caro só para contar
    private void conferirTamanho(COSDocument documento) throws IOException {
        long total = 0;
        byte[] descarte = new byte[64 * 1024];
        for (COSObjectKey chave : List.copyOf(documento.getXrefTable().keySet())) {
            COSBase objeto = documento.getObjectFromPool(chave).getObject();
            if (!(objeto instanceof COSStream parte) || COSName.IMAGE.equals(parte.getCOSName(COSName.SUBTYPE))) {
                continue;
            }
            try (InputStream conteudo = parte.createInputStream()) {
                int lidos;
                while ((lidos = conteudo.read(descarte)) != -1) {
                    total += lidos;
                    if (total > CONTEUDO_MAXIMO) {
                        throw new PdfRecusado("Este PDF é pesado demais para ser lido.");
                    }
                }
            }
        }
    }

    //Só grava uma copia se havia script para tirar: no caso comum a tela mostra o original, sem custo nenhum.
    //O original continua guardado do jeito que chegou (a notificação pode vir assinada, e regravar quebraria a assinatura)
    private byte[] copiaSemScripts(PDDocument documento) throws IOException {
        if (!TiraScripts.tirar(documento.getDocument())) {
            return null;
        }
        //PDF aberto sem senha, mas com restrições (de impressão, por exemplo): a copia sai sem elas,
        //senao o PDFBox nao consegue gravar
        documento.setAllSecurityToBeRemoved(true);
        ByteArrayOutputStream copia = new ByteArrayOutputStream();
        documento.save(copia);
        return copia.toByteArray();
    }

    //O extrator de texto do PDFBox, com um relogio: antes de cada comando da pagina (escrever, desenhar,
    //trocar a fonte...) confere se o tempo acabou. Assim nenhum PDF segura o servidor por mais que o teto
    private static final class ExtratorComPrazo extends PDFTextStripper {

        //nanoTime: um relogio que só anda para frente, bom para medir intervalos (a hora do sistema pode ser acertada)
        private final long limite;

        ExtratorComPrazo(Duration tempoMaximo) {
            this.limite = System.nanoTime() + tempoMaximo.toNanos();
        }

        @Override
        protected void processOperator(Operator comando, List<COSBase> argumentos) throws IOException {
            if (System.nanoTime() - limite >= 0) {
                throw new PdfRecusado("Este PDF demorou demais para ser lido.");
            }
            super.processOperator(comando, argumentos);
        }
    }
}
