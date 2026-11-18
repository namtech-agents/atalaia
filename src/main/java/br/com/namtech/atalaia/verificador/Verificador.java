package br.com.namtech.atalaia.verificador;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

//Confere, só por código, se o que a IA extraiu está mesmo escrito no PDF.
//Dado cujo trecho nao esta no PDF nao passa daqui; se o trecho fala mesmo daquele campo, quem ve é a pessoa
public class Verificador {

    public ResultadoVerificacao conferir(String textoDoPdf, List<DadoExtraido> dados) {
        List<String> motivos = new ArrayList<>();

        //Sem nenhum dado nao ha o que conferir: uma pessoa precisa olhar
        if (dados.isEmpty()) {
            motivos.add("Nenhum dado foi extraído do documento.");
        }

        String texto = arrumar(textoDoPdf);
        for (DadoExtraido dado : dados) {
            String trecho = arrumar(dado.trecho());
            String valor = arrumar(dado.valor());

            //Trecho ou valor sem nenhuma letra ou numero "aparece" em qualquer texto, entao é rejeitado antes de procurar
            if (semConteudo(trecho) || semConteudo(valor)) {
                motivos.add("O dado \"" + dado.campo() + "\" veio sem valor ou sem trecho.");
            } else if (!contemInteiro(texto, trecho)) {
                motivos.add("O trecho do dado \"" + dado.campo() + "\" não está no texto do PDF.");
            } else if (!contemInteiro(trecho, valor)) {
                motivos.add("O valor do dado \"" + dado.campo() + "\" não aparece no trecho de onde ele diz ter saído.");
            }
        }
        return new ResultadoVerificacao(!motivos.isEmpty(), motivos);
    }

    //Deixa os dois lados comparaveis: dado que nao veio (null) vira vazio; o NFKC troca os caracteres "parecidos"
    //pelo comum (o espaço que nao quebra, usado entre "R$" e o numero, vira espaço normal); e, como o PDF quebra
    //a linha onde quer, qualquer sequencia de espaços e quebras vira um espaço só
    private String arrumar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFKC).replaceAll("\\s+", " ").strip();
    }

    private boolean semConteudo(String texto) {
        return texto.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    //O achado tem que aparecer inteiro: "0 dias" nao pode passar por estar dentro de "30 dias",
    //nem "250,00" por estar dentro de "1.250,00". Procura cada lugar onde aparece e ve se esta solto dos dois lados
    private boolean contemInteiro(String onde, String oQue) {
        int inicio = onde.indexOf(oQue);
        while (inicio >= 0) {
            int fim = inicio + oQue.length();
            if (!coladoAntes(onde, inicio) && !coladoDepois(onde, fim)) {
                return true;
            }
            inicio = onde.indexOf(oQue, inicio + 1);
        }
        return false;
    }

    //Colado antes: o caractere anterior é letra ou numero, ou é o ponto ou a virgula de dentro de um numero ("1." antes de "250")
    private boolean coladoAntes(String onde, int inicio) {
        if (inicio == 0) {
            return false;
        }
        char anterior = onde.charAt(inicio - 1);
        boolean pontoDeNumero = (anterior == '.' || anterior == ',')
                && inicio >= 2 && Character.isDigit(onde.charAt(inicio - 2));
        return Character.isLetterOrDigit(anterior) || pontoDeNumero;
    }

    //Colado depois: a mesma ideia do outro lado (",00" depois de "1.250")
    private boolean coladoDepois(String onde, int fim) {
        if (fim == onde.length()) {
            return false;
        }
        char seguinte = onde.charAt(fim);
        boolean pontoDeNumero = (seguinte == '.' || seguinte == ',')
                && fim + 1 < onde.length() && Character.isDigit(onde.charAt(fim + 1));
        return Character.isLetterOrDigit(seguinte) || pontoDeNumero;
    }
}
