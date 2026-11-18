package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;

//Uma regra de prazo: quantos dias, como conta, de que artigo saiu e quando foi conferida.
//suspendeNoRecesso: vale para prazo da Justiça, que para de 20/12 a 20/01.
//conferidaEm: dia em que um contador ou advogado conferiu a regra na norma; null se ninguem conferiu ainda.
public record RegraDePrazo(
        String nome,
        int dias,
        TipoDeContagem contagem,
        boolean suspendeNoRecesso,
        String artigo,
        LocalDate conferidaEm) {

    //Prazo de zero dia ou negativo nao existe: melhor parar aqui do que calcular uma data errada
    public RegraDePrazo {
        if (dias < 1) {
            throw new IllegalArgumentException("O prazo precisa ter pelo menos 1 dia");
        }
    }
}
