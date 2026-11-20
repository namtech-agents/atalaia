package br.com.namtech.atalaia.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;

//A regra dos grupos da tela inicial, sem banco: cada teste monta a linha e o aviso na mao
class EtapaTest {

    private static final Instant AGORA = Instant.parse("2026-11-19T15:00:00Z");
    private static final LocalDate PRAZO = LocalDate.of(2026, 12, 9);

    @Test
    void casoSemAvisoPrecisaDeGenteEDizOQueFalta() {
        //Agir e conferir: sem prazo, a leitura com duvida pede conferencia; sem duvida, pede o prazo;
        //com prazo e sem aviso, pede o aviso
        assertThat(Etapa.de(linha(true, null), null, AGORA)).isEqualTo(Etapa.PRECISA_DE_GENTE);
        assertThat(Etapa.oQueFalta(linha(true, null), null)).isEqualTo("Conferir a leitura");
        assertThat(Etapa.oQueFalta(linha(false, null), null)).isEqualTo("Calcular o prazo");
        assertThat(Etapa.oQueFalta(linha(true, PRAZO), null)).isEqualTo("Avisar o cliente");
    }

    @Test
    void avisoValendoEsperaOClienteEConfirmadoCertoEstaProntoParaFechar() {
        //Arrumar
        AvisoAoCliente aguardando = aviso(AGORA);
        AvisoAoCliente confirmado = aviso(AGORA);
        confirmado.confirmar(PRAZO, AGORA);

        //Agir e conferir: com aviso, a etiqueta do proprio aviso ja explica; "o que falta" fica vazio
        assertThat(Etapa.de(linha(false, PRAZO), aguardando, AGORA)).isEqualTo(Etapa.ESPERANDO_O_CLIENTE);
        assertThat(Etapa.de(linha(false, PRAZO), confirmado, AGORA)).isEqualTo(Etapa.PRONTO_PARA_FECHAR);
        assertThat(Etapa.oQueFalta(linha(false, PRAZO), aguardando)).isNull();
        //A leitura que foi para revisao ja foi olhada por quem calculou o prazo e avisou: segue o aviso
        assertThat(Etapa.de(linha(true, PRAZO), aguardando, AGORA)).isEqualTo(Etapa.ESPERANDO_O_CLIENTE);
    }

    @Test
    void avisoQuePedeAtencaoVoltaParaOEscritorio() {
        //Arrumar: o link venceu sem resposta; o cliente marcou a data errada; a data mudou depois do aviso
        AvisoAoCliente vencido = aviso(AGORA.minus(31, ChronoUnit.DAYS));
        AvisoAoCliente errou = aviso(AGORA);
        errou.confirmar(PRAZO.plusWeeks(1), AGORA);
        AvisoAoCliente velho = aviso(AGORA);

        //Agir e conferir: os tres voltam para "precisa de gente"
        assertThat(Etapa.de(linha(false, PRAZO), vencido, AGORA)).isEqualTo(Etapa.PRECISA_DE_GENTE);
        assertThat(Etapa.de(linha(false, PRAZO), errou, AGORA)).isEqualTo(Etapa.PRECISA_DE_GENTE);
        assertThat(Etapa.de(linha(false, PRAZO.plusDays(2)), velho, AGORA)).isEqualTo(Etapa.PRECISA_DE_GENTE);
    }

    private static NotificacaoNaLista linha(boolean precisaDeRevisao, LocalDate dataLimite) {
        return new NotificacaoNaLista(1L, 1L, "Empresa de Teste", TipoDeDocumento.INTIMACAO, null, precisaDeRevisao, AGORA,
                null, null, dataLimite);
    }

    //Um aviso de um caso com data limite em PRAZO. O caso nao vai ao banco: só serve para o aviso copiar a data
    private static AvisoAoCliente aviso(Instant criadoEm) {
        Notificacao caso = new Notificacao(new Empresa(null, "99999009000100", "Empresa de Teste"), new byte[0],
                "Texto.", TipoDeDocumento.INTIMACAO, List.of(), List.of());
        caso.registrarPrazo(PRAZO.minusDays(20), PRAZO, List.of("Conta de teste."), "pessoa@escritorio.test");
        return new AvisoAoCliente(caso, "token-de-teste", "Resumo.", null, "pessoa@escritorio.test", criadoEm);
    }
}
