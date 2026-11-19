package br.com.namtech.atalaia.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.analista.TipoDeDocumento;

class NotificacaoNaListaTest {

    //Dois dias diferentes (meio-dia em Teresina), para dar para saber qual dos dois a linha mostrou
    private static final Instant CHEGOU = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant RESOLVEU = Instant.parse("2026-11-17T15:00:00Z");

    @Test
    void linhaAbertaMostraQuandoChegouEResolvidaMostraQuandoFoiResolvida() {
        //Arrumar: a mesma notificação, aberta e depois resolvida
        NotificacaoNaLista aberta = new NotificacaoNaLista(1L, 1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false,
                CHEGOU, null, null, null);
        NotificacaoNaLista resolvida = new NotificacaoNaLista(1L, 1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false,
                CHEGOU, Desfecho.ARQUIVADO, RESOLVEU, null);

        //Agir e conferir: cada uma mostra a sua data
        assertThat(aberta.resolvida()).isFalse();
        assertThat(aberta.getQuando()).isEqualTo("10/11/2026 às 12:00");
        assertThat(resolvida.resolvida()).isTrue();
        assertThat(resolvida.getQuando()).isEqualTo("17/11/2026 às 12:00");
    }

    @Test
    void prazoDizQuantoFaltaEAvisaQuandoEstaApertado() {
        //Arrumar: hoje é 18/11/2026
        LocalDate hoje = LocalDate.of(2026, 11, 18);

        //Agir e conferir: vencido, hoje, amanhã, no limite do alerta e com folga
        assertThat(comPrazo(hoje.minusDays(1)).prazo(hoje)).isEqualTo("Venceu em 17/11/2026");
        assertThat(comPrazo(hoje).prazo(hoje)).isEqualTo("Vence hoje");
        assertThat(comPrazo(hoje.plusDays(1)).prazo(hoje)).isEqualTo("Vence amanhã");
        assertThat(comPrazo(hoje.plusDays(3)).prazo(hoje)).isEqualTo("Vence em 3 dias, 21/11/2026");
        assertThat(comPrazo(hoje.minusDays(1)).prazoApertado(hoje)).isTrue();
        assertThat(comPrazo(hoje.plusDays(3)).prazoApertado(hoje)).isTrue();
        assertThat(comPrazo(hoje.plusDays(4)).prazoApertado(hoje)).isFalse();
    }

    @Test
    void prazoDaProximaSemanaFicaNoNivelDoMeio() {
        //Arrumar: hoje é 18/11/2026
        LocalDate hoje = LocalDate.of(2026, 11, 18);

        //Agir e conferir: de 4 a 7 dias é "perto"; apertado nao é perto (cada caso tem uma cor só); 8 dias é folga
        assertThat(comPrazo(hoje.plusDays(3)).prazoPerto(hoje)).isFalse();
        assertThat(comPrazo(hoje.plusDays(4)).prazoPerto(hoje)).isTrue();
        assertThat(comPrazo(hoje.plusDays(7)).prazoPerto(hoje)).isTrue();
        assertThat(comPrazo(hoje.plusDays(8)).prazoPerto(hoje)).isFalse();
    }

    @Test
    void casoResolvidoOuSemDataNaoMostraPrazo() {
        //Agir e conferir
        assertThat(comPrazo(null).temPrazo()).isFalse();
        assertThat(new NotificacaoNaLista(1L, 1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false, CHEGOU,
                Desfecho.ARQUIVADO, RESOLVEU, LocalDate.of(2026, 11, 20)).temPrazo()).isFalse();
        assertThat(comPrazo(LocalDate.of(2026, 11, 20)).temPrazo()).isTrue();
    }

    private NotificacaoNaLista comPrazo(LocalDate dataLimite) {
        return new NotificacaoNaLista(1L, 1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false, CHEGOU, null, null,
                dataLimite);
    }
}
