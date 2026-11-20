package br.com.namtech.atalaia.leitor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

//O trabalho com o PDFBox, num processo Java separado do Atalaia (quem o lança é o LeitorPdf).
//Por quê: o PDF vem de fora e pode ser montado para inchar ao abrir. Um arquivo de 1 MB vira 1 GB na memória, e o
//PDFBox descomprime cada parte INTEIRA antes de deixar alguém contar os bytes (revisão do Hefesto, PR #184).
//Não dá para pôr teto por dentro; dá para pôr por fora: este processo nasce com pouca memória e com hora para
//acabar. Se o PDF estourar, morre só ele, e o Atalaia recusa o arquivo sem que os outros pedidos sintam.
//
//Conversa com o LeitorPdf pela entrada e pela saída do processo:
//entra o PDF (todos os bytes); sai, se deu certo, o texto, o aviso de revisão e a cópia sem scripts.
//O código de saída conta o que houve: 0 leu, SAIDA_RECUSADO (a mensagem vai na saída), SAIDA_NAO_ABRIU
final class LeitorIsolado {

    static final int SAIDA_RECUSADO = 10;
    static final int SAIDA_NAO_ABRIU = 11;

    //Notificação é um documento de poucas paginas. Acima disso é engano ou abuso
    static final int MAXIMO_DE_PAGINAS = 100;

    //Cada letra custa memória (o extrator guarda um objeto grande por letra até o fim da página) e tempo:
    //medido em 19/11, 1 milhão de letras num arquivo de 1 KB prendiam o processo por 10 s. 300 mil = 100 páginas
    //de 3 mil letras, bem acima de uma notificação real (poucas páginas), e o pior caso fica em uns 3 s.
    //O teto também acaba com o desenho que se repete dentro de si mesmo milhares de vezes
    static final int MAXIMO_DE_LETRAS = 300_000;

    private LeitorIsolado() {
    }

    public static void main(String[] argumentos) throws IOException {
        //A saída é só da conversa com o LeitorPdf: qualquer outra escrita (aviso do PDFBox) vai para a saída de erros
        PrintStream conversa = System.out;
        System.setOut(System.err);
        byte[] pdf = System.in.readAllBytes();
        try {
            conversa.write(responder(pdf));
            conversa.flush();
            System.exit(0);
        } catch (PdfRecusado recusa) {
            conversa.write(recusa.getMessage().getBytes(StandardCharsets.UTF_8));
            conversa.flush();
            System.exit(SAIDA_RECUSADO);
        } catch (IOException naoAbriu) {
            //Tem a assinatura de PDF mas não abre: quebrado ou com senha
            System.exit(SAIDA_NAO_ABRIU);
        }
    }

    //Lê e embala a resposta: tamanho + texto, aviso de revisão, tamanho + cópia (-1 = sem cópia)
    private static byte[] responder(byte[] pdf) throws IOException {
        ResultadoLeitura resultado = ler(pdf);
        ByteArrayOutputStream embalagem = new ByteArrayOutputStream();
        DataOutputStream saida = new DataOutputStream(embalagem);
        byte[] texto = resultado.texto().getBytes(StandardCharsets.UTF_8);
        saida.writeInt(texto.length);
        saida.write(texto);
        saida.writeBoolean(resultado.precisaDeRevisao());
        byte[] copia = resultado.semScripts();
        saida.writeInt(copia == null ? -1 : copia.length);
        if (copia != null) {
            saida.write(copia);
        }
        return embalagem.toByteArray();
    }

    static ResultadoLeitura ler(byte[] pdf) throws IOException {
        // Abre o PDF; o try fecha o documento sozinho no fim
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            conferirPaginas(documento);
            String texto;
            try {
                texto = new ExtratorComTeto().getText(documento);
            } catch (UncheckedIOException passouDoTeto) {
                //O extrator só deixa sair erro sem aviso (veja processTextPosition): aqui ele volta a ser recusa
                if (passouDoTeto.getCause() instanceof PdfRecusado recusa) {
                    throw recusa;
                }
                throw passouDoTeto;
            }
            //Pdf so de imagem volta em branco (so espaços e quebras de linhas)
            return new ResultadoLeitura(texto, texto.isBlank(), copiaSemScripts(documento));
        }
    }

    //Conta as paginas uma a uma. O numero que o PDF declara (getNumberOfPages) nao serve:
    //quem monta o arquivo escreve ali o que quiser
    private static void conferirPaginas(PDDocument documento) throws PdfRecusado {
        int paginas = 0;
        for (PDPage pagina : documento.getPages()) {
            paginas++;
            if (paginas > MAXIMO_DE_PAGINAS) {
                throw new PdfRecusado("Este PDF tem páginas demais. O limite é " + MAXIMO_DE_PAGINAS + " páginas.");
            }
        }
    }

    //Só grava uma copia se havia script para tirar: no caso comum a tela mostra o original, sem custo nenhum.
    //O original continua guardado do jeito que chegou (a notificação pode vir assinada, e regravar quebraria a assinatura)
    private static byte[] copiaSemScripts(PDDocument documento) throws IOException {
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

    //O extrator de texto do PDFBox, contando as letras
    private static final class ExtratorComTeto extends PDFTextStripper {

        private int letras;

        //processTextPosition não pode lançar IOException (é a regra do PDFBox): a recusa sai embrulhada
        @Override
        protected void processTextPosition(TextPosition letra) {
            letras++;
            if (letras > MAXIMO_DE_LETRAS) {
                throw new UncheckedIOException(new PdfRecusado("Este PDF é pesado demais para ser lido."));
            }
            super.processTextPosition(letra);
        }
    }
}
