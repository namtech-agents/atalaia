package br.com.namtech.atalaia.verificador;

import java.util.ArrayList;
import java.util.List;

//Confere, só por código, se o que a IA extraiu está mesmo escrito no PDF. Dado inventado nao passa daqui
public class Verificador {

    public ResultadoVerificacao conferir(String textoDoPdf, List<DadoExtraido> dados) {
        List<String> motivos = new ArrayList<>();

        //Sem nenhum dado nao ha o que conferir: uma pessoa precisa olhar
        if (dados.isEmpty()) {
            motivos.add("Nenhum dado foi extraído do documento.");
        }

        String texto = juntarEspacos(textoDoPdf);
        for (DadoExtraido dado : dados) {
            String trecho = juntarEspacos(dado.trecho());
            String valor = juntarEspacos(dado.valor());

            //Trecho ou valor em branco "aparece" em qualquer texto, entao é rejeitado antes de procurar
            if (trecho.isEmpty() || valor.isEmpty()) {
                motivos.add("O dado \"" + dado.campo() + "\" veio sem valor ou sem trecho.");
            } else if (!texto.contains(trecho)) {
                motivos.add("O trecho do dado \"" + dado.campo() + "\" não está no texto do PDF.");
            } else if (!trecho.contains(valor)) {
                motivos.add("O valor do dado \"" + dado.campo() + "\" não aparece no trecho de onde ele diz ter saído.");
            }
        }
        return new ResultadoVerificacao(!motivos.isEmpty(), motivos);
    }

    //O PDF quebra a linha onde quer: troca qualquer sequencia de espaços e quebras por um espaço só
    private String juntarEspacos(String texto) {
        return texto.replaceAll("\\s+", " ").strip();
    }
}
