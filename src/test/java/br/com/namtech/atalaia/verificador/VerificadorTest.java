package br.com.namtech.atalaia.verificador;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.leitor.LeitorPdf;

class VerificadorTest {

    //Texto inventado de uma notificação, com uma quebra de linha no meio da frase do valor
    private static final String TEXTO = """
            Notificação de lançamento
            Prazo: 30 dias a contar da ciência.
            Valor do débito: R$ 1.250,00 (mil duzentos
            e cinquenta reais).
            """;

    @Test
    void dadoQueEstaNoTextoPassaSemRevisao() {
        //Arrumar: um dado com o trecho copiado do texto
        DadoExtraido prazo = new DadoExtraido("prazo", "30 dias", "Prazo: 30 dias a contar da ciência.");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(prazo));

        //Conferir: nao precisa de revisao e nao tem motivo nenhum
        assertThat(resultado.precisaDeRevisao()).isFalse();
        assertThat(resultado.motivos()).isEmpty();
    }

    @Test
    void trechoQueNaoEstaNoTextoMandaParaRevisaoComOMotivo() {
        //Arrumar: um dado com um trecho que nao existe no documento (a IA inventou)
        DadoExtraido prazo = new DadoExtraido("prazo", "15 dias", "Prazo: 15 dias a contar da ciência.");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(prazo));

        //Conferir: vai para revisao e diz qual dado nao bateu
        assertThat(resultado.precisaDeRevisao()).isTrue();
        assertThat(resultado.motivos()).containsExactly("O trecho do dado \"prazo\" não está no texto do PDF.");
    }

    @Test
    void valorQueNaoEstaNoTrechoMandaParaRevisao() {
        //Arrumar: o trecho existe, mas o valor nao é o que esta escrito nele
        DadoExtraido prazo = new DadoExtraido("prazo", "15 dias", "Prazo: 30 dias a contar da ciência.");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(prazo));

        //Conferir: vai para revisao e diz que o valor nao saiu daquele trecho
        assertThat(resultado.precisaDeRevisao()).isTrue();
        assertThat(resultado.motivos()).containsExactly(
                "O valor do dado \"prazo\" não aparece no trecho de onde ele diz ter saído.");
    }

    @Test
    void trechoQuebradoEmDuasLinhasNoPdfPassa() {
        //Arrumar: no texto a frase do valor quebra de linha; o trecho veio numa linha só
        DadoExtraido valor = new DadoExtraido("valor", "R$ 1.250,00",
                "Valor do débito: R$ 1.250,00 (mil duzentos e cinquenta reais).");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(valor));

        //Conferir: a quebra de linha nao atrapalha
        assertThat(resultado.precisaDeRevisao()).isFalse();
    }

    @Test
    void dadoEmBrancoMandaParaRevisao() {
        //Arrumar: um dado com o trecho vazio, que "apareceria" em qualquer texto
        DadoExtraido valor = new DadoExtraido("valor", "R$ 1.250,00", " ");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(valor));

        //Conferir: vai para revisao
        assertThat(resultado.precisaDeRevisao()).isTrue();
        assertThat(resultado.motivos()).containsExactly("O dado \"valor\" veio sem valor ou sem trecho.");
    }

    @Test
    void semNenhumDadoMandaParaRevisao() {
        //Agir: pede a conferencia sem nenhum dado extraido
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of());

        //Conferir: vai para revisao
        assertThat(resultado.precisaDeRevisao()).isTrue();
        assertThat(resultado.motivos()).containsExactly("Nenhum dado foi extraído do documento.");
    }

    @Test
    void confereUmDadoContraOTextoQueOLeitorTirouDoPdf() throws IOException {
        //Arrumar: o Leitor tira o texto do PDF de exemplo
        InputStream arquivo = getClass().getResourceAsStream("/pdfs/notificacao-acentuada.pdf");
        String texto = new LeitorPdf().ler(arquivo.readAllBytes()).texto();
        DadoExtraido prazo = new DadoExtraido("prazo", "30 dias", "Prazo: 30 dias a contar da ciência");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(texto, List.of(prazo));

        //Conferir: o Leitor e o Verificador se entendem, com acento e tudo
        assertThat(resultado.precisaDeRevisao()).isFalse();
    }
}
