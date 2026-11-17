package br.com.namtech.atalaia.prazo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CalculadoraDePrazoTest {

    @Test
    void contaDiasCorridosAPartirDoDiasSeguinteAoDaCiencia() {
        //Arrumar: ciencia numa segunda-feira, 2 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 2);

        //Agir: pede o prazo de 30 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo();
        LocalDate limite = calculadora.contarDiasCorridos(ciencia, 30);

        //Conferir: 30 dias depois é 1º de abril de 2026
        assertThat(limite).isEqualTo(LocalDate.of(2026, 4, 1));

    }
}
