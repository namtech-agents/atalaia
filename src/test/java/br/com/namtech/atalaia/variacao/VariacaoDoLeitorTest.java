package br.com.namtech.atalaia.variacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.prazo.CalculadoraDePrazo;
import br.com.namtech.atalaia.prazo.FeriadosNacionais;
import br.com.namtech.atalaia.prazo.RegraDePrazo;
import br.com.namtech.atalaia.prazo.TipoDeContagem;
import br.com.namtech.atalaia.verificador.DadoExtraido;
import br.com.namtech.atalaia.verificador.ResultadoVerificacao;
import br.com.namtech.atalaia.verificador.Verificador;

//US29: a mesma notificação inventada, mudando uma coisa de cada vez, passa pelo Leitor, pelo Verificador e pela
//Calculadora de verdade. Prova que o caminho por código não se engana quando o documento muda. A IA fica de fora
//daqui (ela muda de resposta e custa chamadas): a parte com IA está no VariacaoComIATest, que só roda com a chave
class VariacaoDoLeitorTest {

    private final LeitorPdf leitor = new LeitorPdf();
    private final Verificador verificador = new Verificador();

    //Os dados que a IA devolveria para a base. O teste confere que continuam conferindo nas variações
    private static final List<DadoExtraido> DADOS_DA_BASE = List.of(
            new DadoExtraido("prazo", "20 dias", DocumentoDeTeste.TRECHO_DO_PRAZO),
            new DadoExtraido("valor", "R$ 1.250,00", "Valor da multa em caso de descumprimento: R$ 1.250,00"));

    private String textoDe(DocumentoDeTeste documento) throws IOException {
        return leitor.ler(documento.pdf()).texto();
    }

    @Test
    void todoDocumentoDeTesteSaiMarcadoComoTeste() throws IOException {
        //Agir e conferir: a base e as três variações trazem a marca no texto que o Leitor tira
        DocumentoDeTeste base = DocumentoDeTeste.base();
        for (DocumentoDeTeste documento : List.of(base, base.comData("10 de novembro de 2026"),
                base.comOrdemTrocada(), base.comCnpj("99.999.002/0001-81"))) {
            assertThat(textoDe(documento)).startsWith(DocumentoDeTeste.MARCA);
        }
    }

    @Test
    void trocarAOrdemDosParagrafosNaoMudaNenhumDadoConferido() throws IOException {
        //Arrumar: a base e a mesma com os parágrafos na ordem contrária
        DocumentoDeTeste base = DocumentoDeTeste.base();
        String textoDaBase = textoDe(base);
        String textoTrocado = textoDe(base.comOrdemTrocada());

        //Conferir: o texto mudou de verdade (senão o teste não provaria nada), mas os parágrafos são os mesmos
        assertThat(textoTrocado).isNotEqualTo(textoDaBase);
        for (String paragrafo : base.paragrafos()) {
            assertThat(textoTrocado).contains(paragrafo);
        }
        //Agir e conferir: os mesmos dados conferem nos dois, sem nenhum motivo de revisão
        assertThat(verificador.conferir(textoTrocado, DADOS_DA_BASE))
                .isEqualTo(verificador.conferir(textoDaBase, DADOS_DA_BASE))
                .isEqualTo(new ResultadoVerificacao(false, List.of()));
    }

    @Test
    void trocarSoOCnpjNaoMudaOPrazo() throws IOException {
        //Arrumar: a base e a mesma com outro CNPJ
        DocumentoDeTeste base = DocumentoDeTeste.base();
        String outroCnpj = textoDe(base.comCnpj("99.999.002/0001-81"));

        //Conferir: o CNPJ novo está no texto e o antigo não
        assertThat(outroCnpj).contains("99.999.002/0001-81").doesNotContain("99.999.001/0001-00");
        //Os dados do prazo ("20 dias") e do valor conferem do mesmo jeito que na base, sem revisão. O prazo do caso
        //sai desse dado, da regra e da ciência (a Calculadora nem recebe o CNPJ): com o mesmo dado, a mesma data
        assertThat(verificador.conferir(outroCnpj, DADOS_DA_BASE))
                .isEqualTo(verificador.conferir(textoDe(base), DADOS_DA_BASE))
                .isEqualTo(new ResultadoVerificacao(false, List.of()));
    }

    @Test
    void mudarSoADataDoDocumentoAndaOPrazoAMesmaQuantidadeDeDias() throws IOException {
        //Arrumar: a base de 03/11 e a mesma uma semana depois, 10/11. A data do documento é o que vira a ciência
        //na tela (quem envia confere e informa); uma semana inteira mantém o dia da semana, então fim de semana
        //e feriado caem igual e a conta tem que andar exatamente 7 dias
        DocumentoDeTeste base = DocumentoDeTeste.base();
        String textoDepois = textoDe(base.comData("10 de novembro de 2026"));
        assertThat(textoDepois).contains("Teresina, 10 de novembro de 2026");
        assertThat(verificador.conferir(textoDepois, DADOS_DA_BASE).precisaDeRevisao()).isFalse();

        //Agir
        LocalDate antes = dataLimite(LocalDate.of(2026, 11, 3));
        LocalDate depois = dataLimite(LocalDate.of(2026, 11, 10));

        //Conferir: a data limite andou os mesmos 7 dias que a ciência
        assertThat(ChronoUnit.DAYS.between(antes, depois)).isEqualTo(7);
    }

    //A conta da regra que o documento cita (20 dias corridos), com os feriados nacionais de verdade
    private static LocalDate dataLimite(LocalDate ciencia) {
        Clock hoje = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), Notificacao.TERESINA);
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(
                FeriadosNacionais.entre(2026, 2027), hoje);
        RegraDePrazo regra = new RegraDePrazo("Resposta à intimação", 20, TipoDeContagem.DIAS_CORRIDOS, false,
                "Norma de Teste, art. 1", LocalDate.of(2026, 11, 1));
        return calculadora.calcular(regra, ciencia).dataLimite();
    }
}
