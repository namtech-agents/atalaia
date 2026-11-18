package br.com.namtech.atalaia.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.analista.TipoDeDocumento;

class NotificacaoNaListaTest {

    //Dois dias diferentes (meio-dia em Teresina), para dar para saber qual dos dois a linha mostrou
    private static final Instant CHEGOU = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant RESOLVEU = Instant.parse("2026-11-17T15:00:00Z");

    @Test
    void linhaAbertaMostraQuandoChegouEResolvidaMostraQuandoFoiResolvida() {
        //Arrumar: a mesma notificação, aberta e depois resolvida
        NotificacaoNaLista aberta = new NotificacaoNaLista(1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false,
                CHEGOU, null, null);
        NotificacaoNaLista resolvida = new NotificacaoNaLista(1L, "Empresa de Teste", TipoDeDocumento.COBRANCA, false,
                CHEGOU, Desfecho.ARQUIVADO, RESOLVEU);

        //Agir e conferir: cada uma mostra a sua data
        assertThat(aberta.resolvida()).isFalse();
        assertThat(aberta.getQuando()).isEqualTo("10/11/2026 às 12:00");
        assertThat(resolvida.resolvida()).isTrue();
        assertThat(resolvida.getQuando()).isEqualTo("17/11/2026 às 12:00");
    }
}
