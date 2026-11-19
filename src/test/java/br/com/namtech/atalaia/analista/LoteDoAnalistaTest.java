package br.com.namtech.atalaia.analista;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import br.com.namtech.atalaia.verificador.ResultadoVerificacao;
import br.com.namtech.atalaia.verificador.Verificador;

//A medição com a IA de verdade: os 20 documentos inventados de src/test/resources/lote.
//So roda quando a chave do Gemini esta na variavel de ambiente GEMINI_API_KEY (custa chamadas e precisa de internet);
//sem a chave, o teste é pulado e o ./mvnw verify continua verde.
//Para rodar só ele: ./mvnw test -Dtest=LoteDoAnalistaTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class LoteDoAnalistaTest {

    //Pausa entre um documento e outro: a faixa gratuita da IA limita os pedidos por minuto
    private static final long PAUSA_EM_MILISSEGUNDOS = 7000;

    @Test
    void acertaOTipoDeNoveEmCadaDezEOitentaPorCentoPassamSemRevisao() throws IOException, InterruptedException {
        //Arrumar: o Analista com o Gemini, o Verificador e a lista dos documentos em ordem
        Analista analista = new Analista(new Gemini(System.getenv("GEMINI_API_KEY")));
        Verificador verificador = new Verificador();
        List<Path> documentos;
        try (Stream<Path> pasta = Files.list(Path.of("src/test/resources/lote"))) {
            //So os .txt: o Mac cria arquivos escondidos na pasta (.DS_Store) que nao sao documentos
            documentos = pasta.filter(arquivo -> arquivo.toString().endsWith(".txt")).sorted().toList();
        }
        //Confere a quantidade antes de gastar as chamadas
        assertThat(documentos).hasSize(20);

        //Agir: analisa e confere um por um, contando os acertos
        int tiposCertos = 0;
        int semRevisao = 0;
        for (Path documento : documentos) {
            String texto = Files.readString(documento, StandardCharsets.UTF_8);
            Analise analise = analista.analisar(texto);
            ResultadoVerificacao verificacao = verificador.conferir(texto, analise.dados());

            //O nome do arquivo diz o tipo esperado: "05-intimacao.txt" espera INTIMACAO
            String esperado = documento.getFileName().toString().replaceAll("^\\d+-|\\.txt$", "").toUpperCase();
            boolean tipoCerto = analise.tipo() != null && analise.tipo().name().equals(esperado);
            boolean passou = !analise.precisaDeRevisao() && !verificacao.precisaDeRevisao();
            if (tipoCerto) {
                tiposCertos++;
            }
            if (passou) {
                semRevisao++;
            }
            //Uma linha por documento, para ler o resultado no terminal
            System.out.println(documento.getFileName() + " | tipo: " + analise.tipo() + (tipoCerto ? " (certo)" : " (ERRADO)")
                    + " | " + (passou ? "sem revisão" : "REVISÃO: " + analise.motivo() + " " + verificacao.motivos())
                    //O rascunho do aviso ao cliente (os documentos do lote sao inventados, pode aparecer no terminal)
                    + " | resumo: " + (analise.resumoSugerido() == null ? "(sem rascunho)" : analise.resumoSugerido()));
            Thread.sleep(PAUSA_EM_MILISSEGUNDOS);
        }
        System.out.println("Tipos certos: " + tiposCertos + " de " + documentos.size()
                + " | Sem revisão: " + semRevisao + " de " + documentos.size());

        //Conferir: US10 pede 9 em cada 10 no tipo (18 de 20); US11 pede 80% sem revisao (16 de 20)
        assertThat(tiposCertos).isGreaterThanOrEqualTo(18);
        assertThat(semRevisao).isGreaterThanOrEqualTo(16);
    }
}
