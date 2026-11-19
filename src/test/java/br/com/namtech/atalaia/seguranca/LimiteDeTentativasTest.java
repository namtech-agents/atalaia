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

        //Agir: as três primeiras passam
        assertThat(limite.reservar("203.0.113.7")).isTrue();
        assertThat(limite.reservar("203.0.113.7")).isTrue();
        assertThat(limite.reservar("203.0.113.7")).isTrue();

        //Conferir: a quarta é recusada
        assertThat(limite.reservar("203.0.113.7")).isFalse();
    }

    @Test
    void cadaEnderecoTemAPropriaConta() {
        //Arrumar: um endereço gasta todas as tentativas
        LimiteDeTentativas limite = gastarTudo("203.0.113.7");

        //Conferir: o vizinho continua livre (um ataque não tranca o escritório de outra pessoa)
        assertThat(limite.reservar("198.51.100.20")).isTrue();
    }

    @Test
    void entrarCertoDevolveAVaga() {
        //Arrumar: três tentativas gastas
        LimiteDeTentativas limite = gastarTudo("203.0.113.7");

        //Agir: a última era a senha certa, e a vaga volta
        limite.devolver("203.0.113.7");

        //Conferir: cabe mais uma, e só uma
        assertThat(limite.reservar("203.0.113.7")).isTrue();
        assertThat(limite.reservar("203.0.113.7")).isFalse();
    }

    @Test
    void depoisDaJanelaATravaSoltaEAContaRecomeca() {
        //Arrumar: travado
        LimiteDeTentativas limite = gastarTudo("203.0.113.7");

        //Agir: um minuto antes de acabar a janela, ainda travado; quando ela acaba, solta
        relogio.adiantar(LimiteDeTentativas.JANELA.minusMinutes(1));
        assertThat(limite.reservar("203.0.113.7")).isFalse();
        relogio.adiantar(Duration.ofMinutes(1));

        //Conferir: a conta recomeça do zero (cabem de novo as três)
        assertThat(limite.reservar("203.0.113.7")).isTrue();
        assertThat(limite.reservar("203.0.113.7")).isTrue();
        assertThat(limite.reservar("203.0.113.7")).isTrue();
    }

    @Test
    void ipv6ContaPeloBlocoInteiro() {
        //Arrumar: três tentativas, cada uma de um endereço diferente do mesmo bloco /64
        LimiteDeTentativas limite = new LimiteDeTentativas(3, relogio);
        limite.reservar("2001:db8:1:2::a");
        limite.reservar("2001:db8:1:2::b");
        limite.reservar("2001:db8:1:2:ffff:ffff:ffff:ffff");

        //Conferir: o bloco está travado, mas outro bloco não
        assertThat(limite.reservar("2001:db8:1:2::c")).isFalse();
        assertThat(limite.reservar("2001:db8:1:3::a")).isTrue();
    }

    @Test
    void memoriaCheiaRecusaEnderecoNovoAteAJanelaAbrirEspaco() {
        //Arrumar: teto de 2 endereços, os dois dentro da janela
        LimiteDeTentativas limite = new LimiteDeTentativas(3, 2, relogio);
        limite.reservar("203.0.113.1");
        limite.reservar("203.0.113.2");

        //Agir e conferir: endereço novo espera; os já guardados seguem
        assertThat(limite.reservar("203.0.113.3")).isFalse();
        assertThat(limite.reservar("203.0.113.1")).isTrue();

        //Agir e conferir: a janela acabou, a limpeza abre espaço e o novo entra
        relogio.adiantar(LimiteDeTentativas.JANELA);
        assertThat(limite.reservar("203.0.113.3")).isTrue();
    }

    private LimiteDeTentativas gastarTudo(String endereco) {
        LimiteDeTentativas limite = new LimiteDeTentativas(3, relogio);
        for (int vez = 0; vez < 3; vez++) {
            limite.reservar(endereco);
        }
        return limite;
    }
}
