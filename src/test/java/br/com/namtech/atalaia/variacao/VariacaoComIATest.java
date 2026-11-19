package br.com.namtech.atalaia.variacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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
    private Map<String, String> extraido(DocumentoDeTeste documento) throws IOException, InterruptedException {
        Analise analise = analista.analisar(leitor.ler(documento.pdf()).texto());
        Thread.sleep(PAUSA_EM_MILISSEGUNDOS);
        Map<String, String> campos = analise.dados().stream()
                .collect(Collectors.toMap(dado -> dado.campo().strip(), DadoExtraido::valor, (um, outro) -> um));
        //Uma linha por documento, para ler o resultado no terminal (os documentos são inventados)
        System.out.println(documento + " -> " + analise.tipo() + " " + campos);
        return campos;
    }

    //O primeiro número do prazo ("20 dias a contar da ciência" -> 20). Sem número, null: a comparação falha
    private static Integer diasDoPrazo(Map<String, String> campos) {
        Matcher numero = Pattern.compile("\\d+").matcher(campos.getOrDefault("prazo", ""));
        return numero.find() ? Integer.valueOf(numero.group()) : null;
    }

    //Os campos que mudaram de uma resposta para a outra, com os dois valores
    private static List<String> diferencas(Map<String, String> uma, Map<String, String> outra) {
        Set<String> campos = new TreeSet<>(uma.keySet());
        campos.addAll(outra.keySet());
        return campos.stream().filter(campo -> !Objects.equals(uma.get(campo), outra.get(campo)))
                .map(campo -> campo + ": " + uma.get(campo) + " / " + outra.get(campo)).toList();
    }

    @Test
    void aIaExtraiOMesmoQuandoSoUmaCoisaMuda() throws IOException, InterruptedException {
        //Arrumar e agir: a base e as três variações
        DocumentoDeTeste base = DocumentoDeTeste.base();
        Map<String, String> daBase = extraido(base);
        Map<String, String> ordemTrocada = extraido(base.comOrdemTrocada());
        Map<String, String> outroCnpj = extraido(base.comCnpj("99.999.002/0001-81"));
        Map<String, String> outraData = extraido(base.comData("10 de novembro de 2026"));

        //Conferir: a base trouxe o prazo e o valor (senão as comparações abaixo não provariam nada)
        assertThat(daBase).containsKeys("prazo", "valor");
        //O que decide o caso fica igual nas três variações: o número de dias do prazo e o valor, letra por letra.
        //O prazo vai pelo número: na rodada de 19/11 a IA escreveu "20 dias a contar da ciência" na base e
        //"20 dias" nas variações. O sentido é o mesmo, e esse texto não entra na conta (a data sai da regra da tela)
        for (Map<String, String> variacao : List.of(ordemTrocada, outroCnpj, outraData)) {
            assertThat(diasDoPrazo(variacao)).isEqualTo(diasDoPrazo(daBase));
            assertThat(variacao.get("valor")).isEqualTo(daBase.get("valor"));
        }
        //Os outros campos só são medidos, não exigidos: na rodada de 19/11, mesmo com temperatura 0, a ordem trocada
        //voltou com o remetente mais curto e, em outra rodada, com o número do termo a mais. Cada dado continua
        //passando pelo Verificador (trecho e valor copiados do PDF), então a variação não vira dado inventado
        System.out.println("Campos diferentes na ordem trocada: " + diferencas(daBase, ordemTrocada));
    }
}
