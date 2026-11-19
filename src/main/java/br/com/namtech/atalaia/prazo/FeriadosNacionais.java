package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//Os feriados que a conta do prazo conhece: só os nacionais que caem todo ano no mesmo dia.
//Feriado estadual, municipal, ponto facultativo e data que muda de ano para ano (Sexta-feira Santa) NAO entram:
//dependem do lugar e do orgao. Por isso toda conta leva o AVISO, e uma pessoa confere a data
public final class FeriadosNacionais {

    public static final String AVISO = "Feriados nesta conta: só os nacionais de data fixa. Feriado estadual, municipal, "
            + "ponto facultativo e Sexta-feira Santa não entram: confira se há algum no período.";

    //1/1, 21/4, 1/5, 7/9, 2/11, 15/11 e 25/12: Lei 662/1949 (redação da Lei 10.607/2002).
    //12/10: Lei 6.802/1980. 20/11: Lei 14.759/2023
    private static final List<MonthDay> DIAS = List.of(MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1),
            MonthDay.of(9, 7), MonthDay.of(10, 12), MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20),
            MonthDay.of(12, 25));

    private FeriadosNacionais() {
    }

    //Todos os feriados do primeiro ao ultimo ano, contando os dois
    public static Set<LocalDate> entre(int primeiroAno, int ultimoAno) {
        return IntStream.rangeClosed(primeiroAno, ultimoAno).boxed()
                .flatMap(ano -> DIAS.stream().map(dia -> dia.atYear(ano)))
                .collect(Collectors.toUnmodifiableSet());
    }
}
