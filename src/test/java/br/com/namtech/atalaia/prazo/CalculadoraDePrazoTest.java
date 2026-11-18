package br.com.namtech.atalaia.prazo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CalculadoraDePrazoTest {

    //Regra inventada para os testes (repositorio publico: nenhuma regra de verdade entra sem conferencia)
    private RegraDePrazo regraDeExemplo(int dias, TipoDeContagem contagem, boolean suspendeNoRecesso) {
        return new RegraDePrazo("Prazo de exemplo", dias, contagem, suspendeNoRecesso,
                "Norma de exemplo, art. 1º", LocalDate.of(2026, 11, 2));
    }

    @Test
    void contaDiasCorridosAPartirDoDiaSeguinteAoDaCiencia() {
        //Arrumar: ciencia numa segunda-feira, 2 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 2);
        RegraDePrazo regra = regraDeExemplo(30, TipoDeContagem.DIAS_CORRIDOS, false);

        //Agir: pede o prazo de 30 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: 30 dias depois é 1º de abril de 2026
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(prazo.precisaDeRevisao()).isFalse();
    }

    @Test
    void contaDiasUteisPulandoSabadoEDomingo() {
        //Arrumar: ciencia numa sexta-feira, 6 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 6);
        RegraDePrazo regra = regraDeExemplo(5, TipoDeContagem.DIAS_UTEIS, false);

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: pula o fim de semana e cai na sexta seguinte, 13 de março
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    void contaDiasUteisPulandoFeriado() {
        //Arrumar: ciencia na segunda, 20 de abril de 2026; terça 21 é Tiradentes
        LocalDate ciencia = LocalDate.of(2026, 4, 20);
        Set<LocalDate> feriados = Set.of(LocalDate.of(2026, 4, 21));
        RegraDePrazo regra = regraDeExemplo(1, TipoDeContagem.DIAS_UTEIS, false);

        //Agir: calculadora que conhece os feriados, prazo de 1 dia util
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(feriados);
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: a terça não conta, o prazo cai na quarta, 22 de abril
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 4, 22));
    }

    @Test
    void contaDiasUteisNaViradaDoAno() {
        //Arrumar: ciencia na terça, 29 de dezembro de 2026; sexta, 1º de janeiro, é feriado
        LocalDate ciencia = LocalDate.of(2026, 12, 29);
        Set<LocalDate> feriados = Set.of(LocalDate.of(2027, 1, 1));
        RegraDePrazo regra = regraDeExemplo(5, TipoDeContagem.DIAS_UTEIS, false);

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(feriados);
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: conta 30 e 31 de dezembro, pula o feriado e o fim de semana,
        //conta 4, 5 e 6 de janeiro. Cai na quarta, 6 de janeiro de 2027
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2027, 1, 6));
    }

    @Test
    void mostraAContaPassoAPassoComOArtigoEAConferencia() {
        //Arrumar: ciencia numa segunda-feira, 2 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 2);
        RegraDePrazo regra = regraDeExemplo(30, TipoDeContagem.DIAS_CORRIDOS, false);

        //Agir: pede o prazo de 30 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: cada passo da conta aparece, na ordem, com o artigo e a data da conferencia
        assertThat(prazo.passos()).containsExactly(
                "Regra: Prazo de exemplo, 30 dias corridos (Norma de exemplo, art. 1º).",
                "Ciência em 02/03/2026.",
                "A contagem começa em 03/03/2026, primeiro dia com expediente depois da ciência.",
                "O último dia da contagem é 01/04/2026. As duas contas deram a mesma data.",
                "Regra conferida na norma em 02/11/2026.",
                "Data limite: 01/04/2026.");
    }

    @Test
    void prazoEmDiasCorridosSoComecaEmDiaComExpediente() {
        //Arrumar: ciencia numa sexta-feira, 6 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 6);
        RegraDePrazo regra = regraDeExemplo(3, TipoDeContagem.DIAS_CORRIDOS, false);

        //Agir: pede o prazo de 3 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: o primeiro dia é a segunda, 9; conta 9, 10 e 11. Cai na quarta, 11 de março
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    void prazoEmDiasCorridosQueVenceNoSabadoVaiParaASegunda() {
        //Arrumar: ciencia numa segunda-feira, 2 de março de 2026
        LocalDate ciencia = LocalDate.of(2026, 3, 2);
        RegraDePrazo regra = regraDeExemplo(5, TipoDeContagem.DIAS_CORRIDOS, false);

        //Agir: pede o prazo de 5 dias corridos, que termina no sabado, 7
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: sabado nao tem expediente, o prazo vai para a segunda, 9 de março, e a conta avisa
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(prazo.passos()).contains(
                "07/03/2026 não tem expediente: o prazo vai para o próximo dia com expediente.");
    }

    @Test
    void prazoDaJusticaParaNoRecessoForense() {
        //Arrumar: ciencia na quarta, 16 de dezembro de 2026, numa regra que para no recesso
        LocalDate ciencia = LocalDate.of(2026, 12, 16);
        RegraDePrazo regra = regraDeExemplo(5, TipoDeContagem.DIAS_UTEIS, true);

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: conta 17 e 18 de dezembro, para de 20/12 a 20/01,
        //conta 21, 22 e 25 de janeiro. Cai na segunda, 25 de janeiro de 2027
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2027, 1, 25));
    }

    @Test
    void regraQueNaoParaNoRecessoContaDezembroNormalmente() {
        //Arrumar: a mesma ciencia de 16 de dezembro, numa regra que nao para no recesso
        LocalDate ciencia = LocalDate.of(2026, 12, 16);
        RegraDePrazo regra = regraDeExemplo(5, TipoDeContagem.DIAS_UTEIS, false);

        //Agir: pede o prazo de 5 dias úteis
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: conta 17, 18, 21, 22 e 23. Cai na quarta, 23 de dezembro
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 12, 23));
    }

    @Test
    void cienciaTacitaValeAlgunsDiasDepoisDaEntrega() {
        //Arrumar: mensagem entregue na segunda, 1º de junho de 2026, que ninguem abriu
        LocalDate entrega = LocalDate.of(2026, 6, 1);
        RegraDePrazo regra = regraDeExemplo(30, TipoDeContagem.DIAS_CORRIDOS, false);

        //Agir: ciencia tacita 15 dias depois da entrega, e ai o prazo de 30 dias corridos
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcularComCienciaTacita(regra, entrega, 15);

        //Conferir: a ciencia vale em 16 de junho; 30 dias depois é quinta, 16 de julho
        assertThat(prazo.dataLimite()).isEqualTo(LocalDate.of(2026, 7, 16));
        assertThat(prazo.passos()).startsWith(
                "Ninguém abriu a mensagem entregue em 01/06/2026: ciência tácita 15 dias depois.",
                "Regra: Prazo de exemplo, 30 dias corridos (Norma de exemplo, art. 1º).",
                "Ciência em 16/06/2026.");
    }

    @Test
    void contasComDatasDiferentesMandamOCasoParaRevisao() {
        //Arrumar: duas contas que nao bateram (uma deu 1º de abril, a outra 2 de abril)
        RegraDePrazo regra = regraDeExemplo(30, TipoDeContagem.DIAS_CORRIDOS, false);
        LocalDate andando = LocalDate.of(2026, 4, 1);
        LocalDate peloCalendario = LocalDate.of(2026, 4, 2);

        //Agir: pede para a calculadora fechar o resultado com as duas datas
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.fechar(regra, andando, peloCalendario, new ArrayList<>());

        //Conferir: fica sem data limite, vai para revisao e a conta diz o motivo
        assertThat(prazo.dataLimite()).isNull();
        assertThat(prazo.precisaDeRevisao()).isTrue();
        assertThat(prazo.passos()).containsExactly(
                "As duas contas deram datas diferentes (01/04/2026 e 02/04/2026): o caso vai para revisão.");
    }

    @Test
    void regraQueNinguemConferiuMandaOCasoParaRevisao() {
        //Arrumar: regra sem data de conferencia (ninguem leu na norma ainda)
        LocalDate ciencia = LocalDate.of(2026, 3, 2);
        RegraDePrazo regra = new RegraDePrazo("Prazo de exemplo", 30, TipoDeContagem.DIAS_CORRIDOS, false,
                "Norma de exemplo, art. 1º", null);

        //Agir: pede o prazo
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(Set.of());
        PrazoCalculado prazo = calculadora.calcular(regra, ciencia);

        //Conferir: fica sem data limite, vai para revisao e a conta diz o motivo
        assertThat(prazo.dataLimite()).isNull();
        assertThat(prazo.precisaDeRevisao()).isTrue();
        assertThat(prazo.passos()).contains(
                "A regra ainda não foi conferida por contador ou advogado: o caso vai para revisão.");
    }
}
