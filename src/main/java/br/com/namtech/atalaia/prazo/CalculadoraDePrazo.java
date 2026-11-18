package br.com.namtech.atalaia.prazo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class CalculadoraDePrazo {

    //Jeito de escrever as datas nos passos: 02/03/2026
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    //Feriados que a calculadora conhece; quem cria a calculadora é que informa
    private final Set<LocalDate> feriados;

    public CalculadoraDePrazo(Set<LocalDate> feriados) {
        this.feriados = feriados;
    }

    //Entrada principal: recebe a regra e o dia da ciencia, devolve a data limite com a conta
    public PrazoCalculado calcular(RegraDePrazo regra, LocalDate ciencia) {
        return calcular(regra, ciencia, new ArrayList<>());
    }

    //Ciencia tacita: ninguem abriu a mensagem, entao a ciencia vale sozinha alguns dias corridos depois da entrega
    public PrazoCalculado calcularComCienciaTacita(RegraDePrazo regra, LocalDate entrega, int diasAteACiencia) {
        LocalDate ciencia = entrega.plusDays(diasAteACiencia);
        List<String> passos = new ArrayList<>();
        passos.add("Ninguém abriu a mensagem entregue em " + DIA.format(entrega) + ": ciência tácita "
                + diasAteACiencia + " dias depois.");
        return calcular(regra, ciencia, passos);
    }

    private PrazoCalculado calcular(RegraDePrazo regra, LocalDate ciencia, List<String> passos) {
        String contagem = regra.contagem() == TipoDeContagem.DIAS_UTEIS ? "dias úteis" : "dias corridos";
        passos.add("Regra: " + regra.nome() + ", " + regra.dias() + " " + contagem + " (" + regra.artigo() + ").");
        passos.add("Ciência em " + DIA.format(ciencia) + ".");

        //O dia da ciencia nao entra, e o prazo so começa a correr em dia com expediente
        LocalDate inicio = proximoDiaComExpediente(ciencia.plusDays(1), regra);
        passos.add("A contagem começa em " + DIA.format(inicio) + ", primeiro dia com expediente depois da ciência.");

        //Calculo duplo: a mesma conta feita de dois jeitos
        LocalDate andando = contarAndando(regra, inicio);
        LocalDate peloCalendario = contarPeloCalendario(regra, inicio);
        return fechar(regra, andando, peloCalendario, passos);
    }

    //Forma 1: anda um dia de cada vez e so conta os que valem para o prazo
    private LocalDate contarAndando(RegraDePrazo regra, LocalDate inicio) {
        LocalDate data = inicio;
        int contados = 1;
        while (contados < regra.dias()) {
            data = data.plusDays(1);
            if (contaNoPrazo(data, regra)) {
                contados++;
            }
        }
        return data;
    }

    //Forma 2: monta a fila de dias a partir do inicio, deixa so os que valem e pega o da posição do prazo
    private LocalDate contarPeloCalendario(RegraDePrazo regra, LocalDate inicio) {
        return Stream.iterate(inicio, dia -> dia.plusDays(1))
                .filter(dia -> contaNoPrazo(dia, regra))
                .skip(regra.dias() - 1)
                .findFirst()
                .orElseThrow();
    }

    //Compara as duas contas e fecha o resultado. Sem private para o teste conseguir entregar duas datas diferentes
    PrazoCalculado fechar(RegraDePrazo regra, LocalDate andando, LocalDate peloCalendario, List<String> passos) {
        if (!andando.equals(peloCalendario)) {
            passos.add("As duas contas deram datas diferentes (" + DIA.format(andando) + " e "
                    + DIA.format(peloCalendario) + "): o caso vai para revisão.");
            return new PrazoCalculado(null, passos, true);
        }
        passos.add("O último dia da contagem é " + DIA.format(andando) + ". As duas contas deram a mesma data.");

        //O prazo so vence em dia com expediente: se cair fora, vai para o proximo
        LocalDate limite = proximoDiaComExpediente(andando, regra);
        if (!limite.equals(andando)) {
            passos.add(DIA.format(andando) + " não tem expediente: o prazo vai para o próximo dia com expediente.");
        }

        //Regra que ninguem conferiu na norma nao pode dar prazo para ninguem
        if (regra.conferidaEm() == null) {
            passos.add("A regra ainda não foi conferida por contador ou advogado: o caso vai para revisão.");
            return new PrazoCalculado(null, passos, true);
        }
        passos.add("Regra conferida na norma em " + DIA.format(regra.conferidaEm()) + ".");
        passos.add("Data limite: " + DIA.format(limite) + ".");
        return new PrazoCalculado(limite, passos, false);
    }

    private LocalDate proximoDiaComExpediente(LocalDate data, RegraDePrazo regra) {
        while (!temExpediente(data, regra)) {
            data = data.plusDays(1);
        }
        return data;
    }

    //Em dias corridos todo dia conta; em dias uteis so conta o dia com expediente
    private boolean contaNoPrazo(LocalDate data, RegraDePrazo regra) {
        return regra.contagem() == TipoDeContagem.DIAS_CORRIDOS || temExpediente(data, regra);
    }

    //Dia com expediente: nao cai em sabado, domingo nem feriado, nem no recesso quando a regra para nele
    private boolean temExpediente(LocalDate data, RegraDePrazo regra) {
        DayOfWeek dia = data.getDayOfWeek();
        boolean fimDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        boolean parado = regra.suspendeNoRecesso() && noRecesso(data);
        return !fimDeSemana && !feriados.contains(data) && !parado;
    }

    //Recesso forense: de 20 de dezembro a 20 de janeiro, contando os dois dias
    private boolean noRecesso(LocalDate data) {
        MonthDay dia = MonthDay.from(data);
        return !dia.isBefore(MonthDay.of(12, 20)) || !dia.isAfter(MonthDay.of(1, 20));
    }
}
