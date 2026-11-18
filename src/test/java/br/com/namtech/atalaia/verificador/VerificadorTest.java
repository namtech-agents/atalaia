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
    void valorQueEPedacoDeOutroNumeroMandaParaRevisao() {
        //Arrumar: "0 dias" esta dentro de "30 dias", e "250,00" dentro de "1.250,00", mas nenhum dos dois é o valor escrito
        DadoExtraido prazo = new DadoExtraido("prazo", "0 dias", "Prazo: 30 dias a contar da ciência.");
        DadoExtraido valor = new DadoExtraido("valor", "250,00", "Valor do débito: R$ 1.250,00");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(prazo, valor));

        //Conferir: os dois sao rejeitados
        assertThat(resultado.motivos()).containsExactly(
                "O valor do dado \"prazo\" não aparece no trecho de onde ele diz ter saído.",
                "O valor do dado \"valor\" não aparece no trecho de onde ele diz ter saído.");
    }

    @Test
    void trechoQueEPedacoDeOutroNumeroMandaParaRevisao() {
        //Arrumar: o documento diz 130 dias; o trecho "30 dias" so existe como pedaço de "130 dias"
        DadoExtraido prazo = new DadoExtraido("prazo", "30 dias", "30 dias");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir("Prazo: 130 dias", List.of(prazo));

        //Conferir: vai para revisao
        assertThat(resultado.motivos()).containsExactly("O trecho do dado \"prazo\" não está no texto do PDF.");
    }

    @Test
    void valorNoFimDaFraseComPontoFinalPassa() {
        //Arrumar: depois de "30 dias" vem o ponto final da frase, que nao faz parte de numero nenhum
        DadoExtraido prazo = new DadoExtraido("prazo", "30 dias", "O prazo é de 30 dias.");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir("O prazo é de 30 dias. Valor: 10", List.of(prazo));

        //Conferir: passa
        assertThat(resultado.precisaDeRevisao()).isFalse();
    }

    @Test
    void espacoQueNaoQuebraDoPdfNaoAtrapalha() {
        //Arrumar: no PDF, entre "R$" e o numero vem o espaço que nao quebra (\u00A0); o dado veio com espaço normal
        String texto = "Valor: R$\u00A01.250,00 devido";
        DadoExtraido valor = new DadoExtraido("valor", "R$ 1.250,00", "Valor: R$ 1.250,00 devido");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(texto, List.of(valor));

        //Conferir: passa
        assertThat(resultado.precisaDeRevisao()).isFalse();
    }

    @Test
    void dadoSemLetraNemNumeroMandaParaRevisao() {
        //Arrumar: valor em branco, trecho que nao veio (null) e trecho que é só um espaço invisivel
        DadoExtraido semValor = new DadoExtraido("prazo", "  ", "Prazo: 30 dias a contar da ciência.");
        DadoExtraido semTrecho = new DadoExtraido("valor", "R$ 1.250,00", null);
        DadoExtraido invisivel = new DadoExtraido("tributo", "\u200B", "\u200B");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(semValor, semTrecho, invisivel));

        //Conferir: os tres sao rejeitados
        assertThat(resultado.motivos()).containsExactly(
                "O dado \"prazo\" veio sem valor ou sem trecho.",
                "O dado \"valor\" veio sem valor ou sem trecho.",
                "O dado \"tributo\" veio sem valor ou sem trecho.");
    }

    @Test
    void umDadoRuimNoMeioDosBonsMandaParaRevisaoSoComOMotivoDele() {
        //Arrumar: um dado certo e um com o trecho inventado
        DadoExtraido prazo = new DadoExtraido("prazo", "30 dias", "Prazo: 30 dias a contar da ciência.");
        DadoExtraido tributo = new DadoExtraido("tributo", "IRPJ", "Tributo: IRPJ");

        //Agir: pede a conferencia
        Verificador verificador = new Verificador();
        ResultadoVerificacao resultado = verificador.conferir(TEXTO, List.of(prazo, tributo));

        //Conferir: vai para revisao, e o unico motivo é o do dado ruim
        assertThat(resultado.precisaDeRevisao()).isTrue();
        assertThat(resultado.motivos()).containsExactly("O trecho do dado \"tributo\" não está no texto do PDF.");
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
    void textoComPrazoOuIntimacaoFalaEmObrigacao() {
        //Agir e conferir: maiuscula, acento e quebra de linha no meio nao escondem o sinal
        Verificador verificador = new Verificador();
        assertThat(verificador.falaEmObrigacao("Fica a empresa INTIMADA a apresentar os livros.")).isTrue();
        assertThat(verificador.falaEmObrigacao("Apresente a defesa, sob\npena de revelia.")).isTrue();
        assertThat(verificador.falaEmObrigacao("PRAZO: 30 dias.")).isTrue();
    }

    @Test
    void avisoSemCobrancaNaoFalaEmObrigacao() {
        //Agir e conferir: um aviso de manutenção, mesmo citando valor, nao tem sinal de obrigação
        Verificador verificador = new Verificador();
        assertThat(verificador.falaEmObrigacao(
                "O portal ficará fora do ar no sábado. A taxa de R$ 10,00 não muda.")).isFalse();
        //palavra comum que só contem um sinal por dentro tambem nao conta
        assertThat(verificador.falaEmObrigacao(
                "A solicitação foi recebida e o documento solicitado sai simultaneamente.")).isFalse();
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
