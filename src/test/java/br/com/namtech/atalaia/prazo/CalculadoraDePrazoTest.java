package br.com.namtech.atalaia.prazo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CalculadoraDePrazoTest {

    @Test
    void contaDiasCorridosAPartirDoDiaSeguinteAoDaCiencia() {
        //Arrumar: ciencia numa segunda-feira, 2 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 2);

        //Agir: pede o prazo de 30 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        LocalDate limite = calculadora.contarDiasCorridos(ciencia, 30);

        //Conferir: 30 dias depois é 1º de abril de 2026
        assertThat(limite).isEqualTo(LocalDate.of(2026, 4, 1));

    }

    @Test
    void contaDiasUteisPulandoSabadoEDomingo() {
        //Arrumar: ciencia numa sexta-feira, 6 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 6);

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        LocalDate limite = calculadora.contarDiasUteis(ciencia, 5);

        //Conferir: pula o fim de semana e cai na sexta seguinte, 13 de março
        assertThat(limite).isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    void contaDiasUteisPulandoFeriado() {
        //Arrumar: ciencia na segunda, 20 de abril de 2026; terça 21 é Tiradentes
        LocalDate ciencia = LocalDate.of(2026, 4, 20);
        Set<LocalDate> feriados = Set.of(LocalDate.of(2026, 4, 21));

        //Agir: calculadora que conhece os feriados, prazo de 1 dia util
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(feriados);
        LocalDate limite = calculadora.contarDiasUteis(ciencia, 1);

        //Conferir: a terça não conta, o prazo cai na quarta, 22 de abril
        assertThat(limite).isEqualTo(LocalDate.of(2026, 4, 22));
    }

    @Test
    void contaDiasUteisNaViradaDoAno() {
        //Arrumar: ciencia na terça, 29 de dezembro de 2026; sexta, 1º de janeiro, é feriado
        LocalDate ciencia = LocalDate.of(2026, 12, 29);
        Set<LocalDate> feriados = Set.of(LocalDate.of(2027, 1, 1));

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(feriados);
        LocalDate limite = calculadora.contarDiasUteis(ciencia, 5);

        //Conferir: conta 30 e 31 de dezembro, pula o feriado e o fim de semana,
        //conta 4, 5 e 6 de janeiro. Cai na quarta, 6 de janeiro de 2027
        assertThat(limite).isEqualTo(LocalDate.of(2027, 1, 6));
    }
}
