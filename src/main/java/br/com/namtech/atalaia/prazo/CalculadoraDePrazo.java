package br.com.namtech.atalaia.prazo;

import java.time.Clock;
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

    //Relogio de onde sai o "hoje". Vem de fora para o teste poder parar o tempo num dia certo
    private final Clock relogio;

    public CalculadoraDePrazo(Set<LocalDate> feriados, Clock relogio) {
        //copyOf: guarda uma copia que ninguem de fora consegue mudar depois
        this.feriados = Set.copyOf(feriados);
        this.relogio = relogio;
    }

    //Entrada principal: recebe a regra e o dia da ciencia, devolve a data limite com a conta
    public PrazoCalculado calcular(RegraDePrazo regra, LocalDate ciencia) {
        return calcular(regra, ciencia, new ArrayList<>());
    }

    //Ciencia tacita: ninguem abriu a mensagem, entao a ciencia vale sozinha alguns dias corridos depois da entrega
    public PrazoCalculado calcularComCienciaTacita(RegraDePrazo regra, LocalDate entrega, int diasAteACiencia) {
        if (diasAteACiencia < 1) {
            throw new IllegalArgumentException("A ciência tácita precisa de pelo menos 1 dia depois da entrega");
        }
        LocalDate ciencia = entrega.plusDays(diasAteACiencia);
        List<String> passos = new ArrayList<>();
        passos.add("Ninguém abriu a mensagem entregue em " + DIA.format(entrega) + ": ciência tácita depois de "
                + diasAteACiencia + " dia(s).");
        return calcular(regra, ciencia, passos);
    }

    private PrazoCalculado calcular(RegraDePrazo regra, LocalDate ciencia, List<String> passos) {
        String contagem = regra.contagem() == TipoDeContagem.DIAS_UTEIS ? "dias úteis" : "dias corridos";
        passos.add("Regra: " + regra.nome() + ", " + regra.dias() + " " + contagem + " (" + regra.artigo() + ").");

        //Regra que ninguem conferiu na norma nao pode dar prazo: para aqui, antes de qualquer data aparecer
        if (regra.conferidaEm() == null) {
            passos.add("A regra ainda não foi conferida por contador ou advogado: o caso vai para revisão.");
            return new PrazoCalculado(null, passos, true);
        }
        passos.add("Regra conferida na norma em " + DIA.format(regra.conferidaEm()) + ". Próxima conferência até "
                + DIA.format(regra.proximaConferencia()) + ".");
        //Conferencia vencida nao trava o prazo, mas o aviso aparece na conta para alguem reconferir
        if (regra.conferenciaVencida(LocalDate.now(relogio))) {
            passos.add("Atenção: a conferência desta regra está vencida. É preciso ler a regra na norma de novo.");
        }
        passos.add("Ciência em " + DIA.format(ciencia) + ".");

        //O dia da ciencia nao entra, e o prazo so começa a correr em dia com expediente.
        //Ciencia num sabado conta igual a ciencia na sexta: começa na segunda. Ha norma que empurra a propria
        //ciencia para o dia util seguinte; isso fica para conferir regra por regra com o contador.
        LocalDate inicio = proximoDiaComExpediente(ciencia.plusDays(1), regra);
        passos.add("A contagem começa em " + DIA.format(inicio) + ", primeiro dia com expediente depois da ciência.");

        //Calculo duplo: a mesma conta feita de dois jeitos. As duas usam a mesma definição de dia que conta,
        //entao a comparação pega erro de contagem, nao erro de feriado ou de recesso
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
            passos.add(DIA.format(andando) + " não tem expediente: o prazo vai para " + DIA.format(limite) + ".");
        }
        passos.add("Data limite: " + DIA.format(limite) + ".");
        return new PrazoCalculado(limite, passos, false);
    }

    private LocalDate proximoDiaComExpediente(LocalDate data, RegraDePrazo regra) {
        while (!temExpediente(data, regra)) {
            data = data.plusDays(1);
        }
        return data;
    }

    //Dia de recesso nunca conta na regra que para nele. Fora isso: em dias corridos todo dia conta,
    //em dias uteis so conta o dia com expediente
    private boolean contaNoPrazo(LocalDate data, RegraDePrazo regra) {
        if (paradoNoRecesso(data, regra)) {
            return false;
        }
        return regra.contagem() == TipoDeContagem.DIAS_CORRIDOS || temExpediente(data, regra);
    }

    //Dia com expediente: nao cai em sabado, domingo nem feriado, nem no recesso quando a regra para nele
    private boolean temExpediente(LocalDate data, RegraDePrazo regra) {
        DayOfWeek dia = data.getDayOfWeek();
        boolean fimDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        return !fimDeSemana && !feriados.contains(data) && !paradoNoRecesso(data, regra);
    }

    private boolean paradoNoRecesso(LocalDate data, RegraDePrazo regra) {
        return regra.suspendeNoRecesso() && noRecesso(data);
    }

    //Recesso forense: de 20 de dezembro a 20 de janeiro, contando os dois dias
    private boolean noRecesso(LocalDate data) {
        MonthDay dia = MonthDay.from(data);
        return !dia.isBefore(MonthDay.of(12, 20)) || !dia.isAfter(MonthDay.of(1, 20));
    }
}
