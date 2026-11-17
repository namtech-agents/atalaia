package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;

public class CalculadoraDePrazo {

    //Conta dias corridos: o dia da ciencia nao entra, o primeiro dia é o seguinte
    public LocalDate contarDiasCorridos(LocalDate ciencia, int dias) {
        return ciencia.plusDays(dias);
    }
}
