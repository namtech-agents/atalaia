package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.analista.TipoDeDocumento;

//O passo do caso (#214): o caso anda pelos quatro passos na ordem em que o escritório trabalha,
//e o caso fechado não tem passo
class PassoTest {

    private final Instant agora = Instant.parse("2026-03-04T12:00:00Z");
    private final LocalDate dataLimite = LocalDate.of(2026, 3, 20);

    private Notificacao caso() {
        Empresa empresa = new Empresa(null, "11222333000181", "Padaria Teste");
        return new Notificacao(empresa, new byte[0], "texto", TipoDeDocumento.INTIMACAO, List.of(), List.of());
    }

    @Test
    void oCasoAndaPelosQuatroPassosNaOrdem() {
        Notificacao caso = caso();
        //Sem data limite: o primeiro passo é o prazo
        assertThat(Passo.de(caso, null, agora)).isEqualTo(Passo.PRAZO);

        caso.registrarPrazo(LocalDate.of(2026, 3, 3), dataLimite, List.of("Conta."), "pessoa@escritorio.test");
        //Com data e sem aviso: avisar o cliente
        assertThat(Passo.de(caso, null, agora)).isEqualTo(Passo.AVISO);

        AvisoAoCliente aviso = new AvisoAoCliente(caso, "token", "Resumo.", null, "pessoa@escritorio.test", agora);
        //Link valendo e ainda sem resposta: é esperar o cliente
        assertThat(Passo.de(caso, aviso, agora)).isEqualTo(Passo.CLIENTE);

        aviso.confirmar(dataLimite, agora);
        //Confirmou com a data certa: fechar
        assertThat(Passo.de(caso, aviso, agora)).isEqualTo(Passo.FECHAR);

        caso.resolver(Desfecho.ARQUIVADO, "Feito.");
        assertThat(Passo.de(caso, aviso, agora)).isNull();
    }

    @Test
    void clienteQueMarcouADataErradaVoltaParaAvisar() {
        Notificacao caso = caso();
        caso.registrarPrazo(LocalDate.of(2026, 3, 3), dataLimite, List.of("Conta."), "pessoa@escritorio.test");
        AvisoAoCliente aviso = new AvisoAoCliente(caso, "token", "Resumo.", null, "pessoa@escritorio.test", agora);
        //Confirmou, mas marcou outra data: o escritório precisa falar com ele de novo
        aviso.confirmar(dataLimite.plusDays(2), agora);
        assertThat(Passo.de(caso, aviso, agora)).isEqualTo(Passo.AVISO);
    }

    @Test
    void linkVencidoSemRespostaVoltaParaAvisar() {
        Notificacao caso = caso();
        caso.registrarPrazo(LocalDate.of(2026, 3, 3), dataLimite, List.of("Conta."), "pessoa@escritorio.test");
        AvisoAoCliente aviso = new AvisoAoCliente(caso, "token", "Resumo.", null, "pessoa@escritorio.test", agora);
        //Um ano depois, o link já venceu e o cliente nunca respondeu
        assertThat(Passo.de(caso, aviso, agora.plusSeconds(365L * 24 * 3600))).isEqualTo(Passo.AVISO);
    }

    @Test
    void dataQueMudouDepoisDoAvisoVoltaParaAvisar() {
        Notificacao caso = caso();
        caso.registrarPrazo(LocalDate.of(2026, 3, 3), dataLimite, List.of("Conta."), "pessoa@escritorio.test");
        AvisoAoCliente aviso = new AvisoAoCliente(caso, "token", "Resumo.", null, "pessoa@escritorio.test", agora);
        aviso.confirmar(dataLimite, agora);
        //A conta foi refeita: o cliente viu uma data que não vale mais
        caso.registrarPrazo(LocalDate.of(2026, 3, 3), dataLimite.plusDays(5), List.of("Refeita."), "pessoa@escritorio.test");
        assertThat(Passo.de(caso, aviso, agora)).isEqualTo(Passo.AVISO);
    }
}
