package br.com.namtech.atalaia.analista;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

//O teto de chamadas à IA ao mesmo tempo (issue #144): cada chamada segura uma linha do servidor por até 60 s,
//então muitas juntas travariam o Atalaia para todo mundo
class TetoDeChamadasTest {

    @Test
    void chamadaAlemDoTetoDesisteSemChamarAIa() throws Exception {
        //Arrumar: teto de 1 e uma IA que fica "pensando" até o teste soltar
        CountDownLatch pensando = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        TetoDeChamadas teto = new TetoDeChamadas(pergunta -> {
            pensando.countDown();
            try {
                soltar.await();
            } catch (InterruptedException erro) {
                Thread.currentThread().interrupt();
            }
            return "resposta";
        }, 1, Duration.ZERO);
        CompletableFuture<String> primeira = CompletableFuture.supplyAsync(() -> teto.responder("a"));
        assertThat(pensando.await(5, TimeUnit.SECONDS)).isTrue();

        //Agir e conferir: a segunda chega com a vaga ocupada e desiste na hora (o Analista manda o caso para uma pessoa)
        assertThatThrownBy(() -> teto.responder("b")).isInstanceOf(IllegalStateException.class);

        //Conferir: a primeira termina, devolve a vaga, e a próxima chamada passa
        soltar.countDown();
        assertThat(primeira.get(5, TimeUnit.SECONDS)).isEqualTo("resposta");
        assertThat(teto.responder("c")).isEqualTo("resposta");
    }

    @Test
    void falhaDaIaDevolveAVaga() {
        //Arrumar: teto de 1 e uma IA fora do ar
        TetoDeChamadas teto = new TetoDeChamadas(pergunta -> {
            throw new IllegalStateException("fora do ar");
        }, 1, Duration.ZERO);

        //Agir e conferir: as duas chamadas chegam até a IA (a falha da primeira não prendeu a vaga)
        assertThatThrownBy(() -> teto.responder("a")).hasMessage("fora do ar");
        assertThatThrownBy(() -> teto.responder("b")).hasMessage("fora do ar");
    }
}
