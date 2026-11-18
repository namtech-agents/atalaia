package br.com.namtech.atalaia.prazo;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class CalculadoraDePrazo {

    //Conta dias corridos: o dia da ciencia nao entra, o primeiro dia é o seguinte
    public LocalDate contarDiasCorridos(LocalDate ciencia, int dias) {
        return ciencia.plusDays(dias);
    }

    //Conta dias uteis: anda um dia de cada vez e so conta os que sao uteis
    public LocalDate contarDiasUteis(LocalDate ciencia, int dias) {
        LocalDate data = ciencia;
        int contados = 0;
        while (contados < dias) {
            data = data.plusDays(1);
            if (ehDiaUtil(data)) {
                contados++;
            }
        }
        return data;
    }

    //Por enquanto, dia util é o que nao cai em sabado nem domingo
    private boolean ehDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }
}

