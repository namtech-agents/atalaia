package br.com.namtech.atalaia.prazo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public class CalculadoraDePrazo {

    //Feriados que a calculadora conhece; quem cria a calculadora é que informa
    private final Set<LocalDate> feriados;

    public CalculadoraDePrazo(Set<LocalDate> feriados) {
        this.feriados = feriados;
    }

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

    //Dia util é o que nao cai em sabado, domingo nem feriado
    private boolean ehDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !feriados.contains(data);
    }
}

