package br.com.namtech.atalaia.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

//A conta da trava, sem servidor: quantas tentativas cada endereço já fez e quando a janela recomeça
class LimiteDeTentativasTest {

    //Relógio que o teste adianta na mão. Assim "esperar 15 minutos" leva zero segundo
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-11-19T12:00:00Z");

        void adiantar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private final RelogioDeTeste relogio = new RelogioDeTeste();

    @Test
    void travaDepoisDoMaximoDeTentativas() {
        //Arrumar: máximo de 3
        LimiteDeTentativas limite = new LimiteDeTentativas(3, relogio);

        //Agir: duas tentativas ainda deixam passar; a terceira completa o máximo
        limite.contar("203.0.113.7");
        limite.contar("203.0.113.7");
        assertThat(limite.travado("203.0.113.7")).isFalse();
        limite.contar("203.0.113.7");

        //Conferir: travado
        assertThat(limite.travado("203.0.113.7")).isTrue();
    }

    @Test
    void cadaEnderecoTemAProprioConta() {
        //Arrumar: um endereço gasta todas as tentativas
        LimiteDeTentativas limite = new LimiteDeTentativas(3, relogio);
        for (int vez = 0; vez < 3; vez++) {
            limite.contar("203.0.113.7");
        }

        //Conferir: o vizinho continua livre (um ataque não tranca o escritório de outra pessoa)
        assertThat(limite.travado("198.51.100.20")).isFalse();
    }

    @Test
    void depoisDaJanelaATravaSoltaEAContaRecomeca() {
        //Arrumar: travado
        LimiteDeTentativas limite = new LimiteDeTentativas(3, relogio);
        for (int vez = 0; vez < 3; vez++) {
            limite.contar("203.0.113.7");
        }

        //Agir: um minuto antes de acabar a janela, ainda travado; quando ela acaba, solta
        relogio.adiantar(LimiteDeTentativas.JANELA.minusMinutes(1));
        assertThat(limite.travado("203.0.113.7")).isTrue();
        relogio.adiantar(Duration.ofMinutes(1));

        //Conferir: livre, e uma tentativa nova conta do zero
        assertThat(limite.travado("203.0.113.7")).isFalse();
        limite.contar("203.0.113.7");
        assertThat(limite.travado("203.0.113.7")).isFalse();
    }
}
