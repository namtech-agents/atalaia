package br.com.namtech.atalaia.analista;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import br.com.namtech.atalaia.verificador.Verificador;

//O critério da US25 com a IA de verdade: 10 notificações, 3 delas adulteradas de propósito, e o Atalaia segura as 3.
//As 7 em ordem vêm do lote (src/test/resources/lote); as 3 adulteradas (src/test/resources/adulterados) são cópias
//de documentos do lote com um defeito cada: valor por extenso que não bate, prazo em branco, prazo por extenso que não bate.
//Só roda com a chave do Gemini em GEMINI_API_KEY, como o LoteDoAnalistaTest. Para rodar só ele:
//./mvnw test -Dtest=SegundaPassadaComIATest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class SegundaPassadaComIATest {

    //A faixa gratuita da IA limita os pedidos por minuto, e aqui cada documento pode fazer duas perguntas
    private static final long PAUSA_EM_MILISSEGUNDOS = 10000;

    private static final List<String> EM_ORDEM = List.of("01-citacao.txt", "04-citacao.txt", "05-intimacao.txt",
            "09-cobranca.txt", "13-exclusao.txt", "17-informativo.txt", "19-informativo.txt");

    @Test
    void seguraAsTresAdulteradasESoltaQuaseTodasAsEmOrdem() throws IOException, InterruptedException {
        //Arrumar: o Analista com o Gemini, o Verificador e os 10 documentos
        Analista analista = new Analista(new Gemini(System.getenv("GEMINI_API_KEY")));
        Verificador verificador = new Verificador();
        List<Path> adulterados;
        try (Stream<Path> pasta = Files.list(Path.of("src/test/resources/adulterados"))) {
            adulterados = pasta.filter(arquivo -> arquivo.toString().endsWith(".txt")).sorted().toList();
        }
        assertThat(adulterados).hasSize(3);

        //Agir: cada documento passa pelo mesmo caminho do envio (RecepcaoController.analisar)
        int emOrdemSoltos = 0;
        for (String nome : EM_ORDEM) {
            if (motivos(analista, verificador, Path.of("src/test/resources/lote", nome)).isEmpty()) {
                emOrdemSoltos++;
            }
        }
        int adulteradosSeguros = 0;
        for (Path documento : adulterados) {
            if (!motivos(analista, verificador, documento).isEmpty()) {
                adulteradosSeguros++;
            }
        }
        System.out.println("Adulterados seguros: " + adulteradosSeguros + " de 3 | Em ordem sem revisão: "
                + emOrdemSoltos + " de " + EM_ORDEM.size());

        //Conferir: as 3 adulteradas seguras, sem exceção. Das 7 em ordem, a mesma régua do lote (80% sem revisão):
        //uma segunda passada que segura tudo "passaria" no primeiro critério sem servir para nada
        assertThat(adulteradosSeguros).isEqualTo(3);
        assertThat(emOrdemSoltos).isGreaterThanOrEqualTo(6);
    }

    //Os motivos de revisão de um documento, na mesma ordem do envio. Imprime uma linha por documento
    private List<String> motivos(Analista analista, Verificador verificador, Path documento)
            throws IOException, InterruptedException {
        String texto = Files.readString(documento, StandardCharsets.UTF_8);
        Analise analise = analista.analisar(texto);
        List<String> motivos = new ArrayList<>();
        if (analise.motivo() != null) {
            motivos.add(analise.motivo());
        }
        if (analise.tipo() != null) {
            motivos.addAll(verificador.conferir(texto, analise.dados()).motivos());
        }
        if (analise.tipo() == TipoDeDocumento.INFORMATIVO && verificador.falaEmObrigacao(texto)) {
            motivos.add("Informativo que fala em obrigação.");
        }
        if (motivos.isEmpty()) {
            String duvida = analista.procurarDuvida(texto);
            if (duvida != null) {
                motivos.add(duvida);
            }
        }
        System.out.println(documento.getFileName() + " | " + (motivos.isEmpty() ? "sem revisão" : "REVISÃO: " + motivos));
        Thread.sleep(PAUSA_EM_MILISSEGUNDOS);
        return motivos;
    }
}
