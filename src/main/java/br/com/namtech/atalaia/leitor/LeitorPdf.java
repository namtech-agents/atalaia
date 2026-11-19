package br.com.namtech.atalaia.leitor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

//Lê o PDF enviado num processo Java à parte (o LeitorIsolado), com pouca memória e hora para acabar.
//Daqui de fora o Atalaia só manda o PDF, espera e traduz o que voltou. Qualquer jeito de o processo morrer
//(memória estourada, tempo esgotado, defeito do PDFBox) vira "PDF recusado", nunca o servidor caído
public class LeitorPdf {

    public static final int MAXIMO_DE_PAGINAS = LeitorIsolado.MAXIMO_DE_PAGINAS;

    //Uma notificação normal é lida em 1 a 2 s, já contando o processo nascer
    static final Duration TEMPO_MAXIMO = Duration.ofSeconds(20);

    //Cada leitura usa até 256 MB fora do Atalaia. Duas ao mesmo tempo cabem num servidor pequeno;
    //a terceira espera a vez (static: vale para todo o Atalaia, não para cada LeitorPdf criado)
    private static final Semaphore VAGAS = new Semaphore(2, true);

    static final String PESADO_DEMAIS = "Este PDF é pesado demais para ser lido.";

    private final Duration tempoMaximo;

    public LeitorPdf() {
        this(TEMPO_MAXIMO);
    }

    //Para os testes conseguirem um leitor sem tempo nenhum, sem precisar de um PDF que demore de verdade
    LeitorPdf(Duration tempoMaximo) {
        this.tempoMaximo = tempoMaximo;
    }

    public ResultadoLeitura ler(byte[] pdf) throws IOException {
        try {
            if (!VAGAS.tryAcquire(tempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new PdfRecusado("O Atalaia está lendo outros PDFs agora. Espere um instante e envie de novo.");
            }
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new IOException(interrompido);
        }
        try {
            return lerNoProcesso(pdf);
        } finally {
            VAGAS.release();
        }
    }

    private ResultadoLeitura lerNoProcesso(byte[] pdf) throws IOException {
        //A saída de erros do filho (avisos do PDFBox) vai para o registro do próprio Atalaia
        Process filho = new ProcessBuilder(comando()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            //A resposta é lida ao mesmo tempo, noutra linha de execução: se ninguém lesse, o filho travaria
            //com a saída cheia, e esta aqui travaria esperando ele terminar
            CompletableFuture<byte[]> resposta = CompletableFuture.supplyAsync(() -> lerTudo(filho.getInputStream()));
            try (OutputStream entrada = filho.getOutputStream()) {
                entrada.write(pdf);
            } catch (IOException filhoJaMorreu) {
                //O filho morreu antes de ler tudo (estourou a memória ao nascer?): o código de saída diz o resto
            }
            if (!filho.waitFor(tempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new PdfRecusado("Este PDF demorou demais para ser lido.");
            }
            return traduzir(filho.exitValue(), resposta.get());
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new IOException(interrompido);
        } catch (ExecutionException falhaAoLer) {
            throw new IOException(falhaAoLer.getCause());
        } finally {
            //Em qualquer saída daqui (tempo esgotado, erro) o filho não fica vivo gastando memória
            filho.destroyForcibly();
        }
    }

    private static ResultadoLeitura traduzir(int codigo, byte[] resposta) throws IOException {
        if (codigo == LeitorIsolado.SAIDA_RECUSADO) {
            throw new PdfRecusado(new String(resposta, StandardCharsets.UTF_8));
        }
        if (codigo == LeitorIsolado.SAIDA_NAO_ABRIU) {
            throw new IOException("O PDF não abriu");
        }
        if (codigo != 0) {
            //Memória estourada (ExitOnOutOfMemoryError) ou outra queda do filho
            throw new PdfRecusado(PESADO_DEMAIS);
        }
        DataInputStream entrada = new DataInputStream(new ByteArrayInputStream(resposta));
        String texto = new String(entrada.readNBytes(entrada.readInt()), StandardCharsets.UTF_8);
        boolean precisaDeRevisao = entrada.readBoolean();
        int tamanhoDaCopia = entrada.readInt();
        byte[] copia = tamanhoDaCopia < 0 ? null : entrada.readNBytes(tamanhoDaCopia);
        return new ResultadoLeitura(texto, precisaDeRevisao, copia);
    }

    private static byte[] lerTudo(InputStream saidaDoFilho) {
        try {
            return saidaDoFilho.readAllBytes();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    //A linha de comando do filho: o mesmo Java e as mesmas classes do Atalaia, com outra porta de entrada
    static List<String> comando() {
        List<String> comando = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                //Memória curta, e morrer de vez quando ela acabar (sem isso o processo pode ficar agonizando)
                "-Xmx256m", "-XX:+ExitOnOutOfMemoryError", "-XX:+UseSerialGC",
                //Nasce mais rápido: o filho vive poucos segundos e não compensa otimizar o código a fundo
                "-XX:TieredStopAtLevel=1", "-Djava.awt.headless=true",
                "-cp", System.getProperty("java.class.path")));
        //No ar o Atalaia roda de dentro de um único .jar do Spring Boot, que guarda as bibliotecas em BOOT-INF.
        //Nesse caso quem abre o jar é o lançador do Spring Boot, avisado de qual classe chamar
        if (LeitorPdf.class.getClassLoader().getClass().getName().startsWith("org.springframework.boot.loader")) {
            comando.add("-Dloader.main=" + LeitorIsolado.class.getName());
            comando.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            comando.add(LeitorIsolado.class.getName());
        }
        return comando;
    }
}
