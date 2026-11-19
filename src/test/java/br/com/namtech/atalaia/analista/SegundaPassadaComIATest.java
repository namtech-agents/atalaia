package br.com.namtech.atalaia.analista;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

//O critério da US25 com a IA de verdade: 10 notificações, 3 delas adulteradas de propósito, e o Atalaia segura as 3.
//As 3 adulteradas (src/test/resources/adulterados) são cópias de 02-citacao, 06-intimacao e 10-cobranca do lote com
//uma linha mudada: prazo por extenso que não bate, prazo em branco, valor por extenso que não bate. Os 3 originais
//estão entre os 7 em ordem: assim o teste prova que quem segura é o defeito, e não o documento.
//Os outros 4 em ordem cobrem os tipos que faltam (exclusão e informativo) e mais uma citação e uma intimação.
//Só roda com a chave do Gemini em GEMINI_API_KEY, como o LoteDoAnalistaTest. Para rodar só ele:
//./mvnw test -Dtest=SegundaPassadaComIATest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class SegundaPassadaComIATest {

    //A faixa gratuita da IA limita os pedidos por minuto, e aqui cada documento pode fazer duas perguntas
    private static final long PAUSA_EM_MILISSEGUNDOS = 10000;

    private static final Path LOTE = Path.of("src/test/resources/lote");
    private static final Path ADULTERADOS = Path.of("src/test/resources/adulterados");

    //Cada adulterada ao lado do original de onde saiu
    private static final List<List<String>> PARES = List.of(
            List.of("02-citacao.txt", "prazo-em-conflito.txt"),
            List.of("06-intimacao.txt", "prazo-em-branco.txt"),
            List.of("10-cobranca.txt", "valor-em-conflito.txt"));

    private static final List<String> OUTROS_EM_ORDEM = List.of("01-citacao.txt", "05-intimacao.txt",
            "13-exclusao.txt", "17-informativo.txt");

    @Test
    void seguraAsTresAdulteradasPelaSegundaLeituraESoltaQuaseTodasAsEmOrdem() throws IOException, InterruptedException {
        //Arrumar: o Analista com o Gemini
        Analista analista = new Analista(new Gemini(System.getenv("GEMINI_API_KEY")));

        //Agir: cada documento passa pelo mesmo caminho do envio (Analista.motivosDaRevisao)
        int emOrdemSoltos = 0;
        int seguradasPelaSegundaLeitura = 0;
        for (List<String> par : PARES) {
            if (motivos(analista, LOTE.resolve(par.get(0))).isEmpty()) {
                emOrdemSoltos++;
            }
            List<String> daAdulterada = motivos(analista, ADULTERADOS.resolve(par.get(1)));
            //Conta só se foi a segunda passada que segurou: o defeito é do tipo que ela existe para achar
            if (daAdulterada.size() == 1 && daAdulterada.get(0).startsWith("A segunda leitura achou")) {
                seguradasPelaSegundaLeitura++;
            }
        }
        for (String nome : OUTROS_EM_ORDEM) {
            if (motivos(analista, LOTE.resolve(nome)).isEmpty()) {
                emOrdemSoltos++;
            }
        }
        System.out.println("Adulteradas seguras pela segunda leitura: " + seguradasPelaSegundaLeitura
                + " de 3 | Em ordem sem revisão: " + emOrdemSoltos + " de 7");

        //Conferir: as 3 adulteradas seguras, sem exceção. Das 7 em ordem, a mesma régua do lote (80% sem revisão):
        //uma segunda passada que segura tudo "passaria" no primeiro critério sem servir para nada
        assertThat(seguradasPelaSegundaLeitura).isEqualTo(3);
        assertThat(emOrdemSoltos).isGreaterThanOrEqualTo(6);
    }

    //Os motivos de revisão de um documento. Imprime uma linha por documento
    private List<String> motivos(Analista analista, Path documento) throws IOException, InterruptedException {
        String texto = Files.readString(documento, StandardCharsets.UTF_8);
        List<String> motivos = analista.motivosDaRevisao(analista.analisar(texto), texto);
        System.out.println(documento.getFileName() + " | " + (motivos.isEmpty() ? "sem revisão" : "REVISÃO: " + motivos));
        Thread.sleep(PAUSA_EM_MILISSEGUNDOS);
        return motivos;
    }
}
