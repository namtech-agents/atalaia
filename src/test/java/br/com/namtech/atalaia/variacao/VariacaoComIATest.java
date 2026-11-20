package br.com.namtech.atalaia.variacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import br.com.namtech.atalaia.analista.Analise;
import br.com.namtech.atalaia.analista.Analista;
import br.com.namtech.atalaia.analista.Gemini;
import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.verificador.DadoExtraido;

//US29 com a IA de verdade: a base e cada variação passam pelo Leitor e pelo Analista (Gemini), e o teste compara
//o que a IA extraiu. Só roda com a chave na variável GEMINI_API_KEY (custa 4 chamadas e precisa de internet);
//sem a chave, é pulado e o ./mvnw verify continua verde. Para rodar só ele: ./mvnw test -Dtest=VariacaoComIATest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class VariacaoComIATest {

    //Pausa entre uma chamada e outra: a faixa gratuita da IA limita os pedidos por minuto
    private static final long PAUSA_EM_MILISSEGUNDOS = 7000;

    private final LeitorPdf leitor = new LeitorPdf();
    private final Analista analista = new Analista(new Gemini(System.getenv("GEMINI_API_KEY")));

    //O que a IA extraiu, como "campo -> valor". O trecho fica de fora: na ordem trocada ele pode vir recortado
    //diferente e continuar certo (quem confere o trecho é o Verificador, no outro teste)
    private boolean primeira = true;

    private Map<String, String> extraido(DocumentoDeTeste documento) throws IOException, InterruptedException {
        //Pausa antes de cada chamada, a partir da segunda (depois da última não precisa esperar)
        if (!primeira) {
            Thread.sleep(PAUSA_EM_MILISSEGUNDOS);
        }
        primeira = false;
        Analise analise = analista.analisar(leitor.ler(documento.pdf()).texto());
        //Campo repetido (dois "prazo" diferentes) ou sem valor faz o teste falhar com a mensagem certa,
        //em vez de um ficar escondido atrás do outro
        Map<String, String> campos = analise.dados().stream()
                .collect(Collectors.toMap(dado -> dado.campo().strip(),
                        dado -> Objects.requireNonNull(dado.valor(), "campo sem valor: " + dado.campo()),
                        (um, outro) -> {
                            throw new AssertionError("A IA mandou o mesmo campo duas vezes: " + um + " / " + outro);
                        }));
        //Uma linha por documento, para ler o resultado no terminal (os documentos são inventados)
        System.out.println(documento + " -> " + analise.tipo() + " " + campos);
        return campos;
    }

    //O primeiro número do prazo ("20 dias a contar da ciência" -> 20). Sem número, null: a comparação falha
    private static Integer diasDoPrazo(Map<String, String> campos) {
        Matcher numero = Pattern.compile("\\d+").matcher(campos.getOrDefault("prazo", ""));
        return numero.find() ? Integer.valueOf(numero.group()) : null;
    }

    @Test
    void aIaExtraiOMesmoQuandoSoUmaCoisaMuda() throws IOException, InterruptedException {
        //Arrumar e agir: a base e as três variações
        DocumentoDeTeste base = DocumentoDeTeste.base();
        Map<String, String> daBase = extraido(base);
        Map<String, String> ordemTrocada = extraido(base.comOrdemTrocada());
        Map<String, String> outroCnpj = extraido(base.comCnpj("99.999.002/0001-81"));
        Map<String, String> outraData = extraido(base.comData("10 de novembro de 2026"));

        //Conferir: a base trouxe o prazo de 20 dias e o valor certo (senão as comparações abaixo compararam
        //vazio com vazio e não provariam nada)
        assertThat(diasDoPrazo(daBase)).isEqualTo(20);
        assertThat(daBase).containsEntry("valor", "R$ 1.250,00");
        //O que decide o caso fica igual nas três variações: o número de dias do prazo e o valor, letra por letra.
        //O prazo vai pelo número: na rodada de 19/11 a IA escreveu "20 dias a contar da ciência" na base e
        //"20 dias" na ordem trocada. O sentido é o mesmo, e esse texto não entra na conta (a data sai da regra da tela)
        for (Map<String, String> variacao : List.of(ordemTrocada, outroCnpj, outraData)) {
            assertThat(diasDoPrazo(variacao)).isEqualTo(diasDoPrazo(daBase));
            assertThat(variacao.get("valor")).isEqualTo(daBase.get("valor"));
        }
        //Os outros campos não são exigidos: a IA varia de uma chamada para outra (em 19/11, na ordem trocada, veio
        //o número do termo a mais numa rodada e o remetente mais curto em outra). Cada dado continua passando pelo
        //Verificador (trecho e valor copiados do PDF), então a variação não vira dado inventado.
        //A linha impressa de cada documento (acima) mostra tudo para quem quiser comparar
    }
}
