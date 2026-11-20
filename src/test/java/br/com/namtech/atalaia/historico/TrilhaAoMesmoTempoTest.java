package br.com.namtech.atalaia.historico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Vários eventos gravados no mesmo caso ao mesmo tempo, cada um na sua conversa com o banco. Sem a tranca do caso
//no Historico.registrar, dois leriam a mesma "última assinatura" e a corrente se dividiria (a conferência acusaria).
//Este teste não tem Transactional: cada conversa grava de verdade. Por isso ele mesmo apaga o que criou, no fim
@SpringBootTest
class TrilhaAoMesmoTempoTest {

    private static final int AO_MESMO_TEMPO = 6;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    @Autowired
    private Historico historico;

    @Autowired
    private BancoDeTeste banco;

    @Autowired
    private JdbcTemplate sql;

    //TransactionTemplate: abre uma conversa com o banco, roda o que estiver dentro e grava no fim
    @Autowired
    private TransactionTemplate conversa;

    private Escritorio escritorio;
    private Empresa empresa;
    private Long id;

    @AfterEach
    void apagaOQueCriou() {
        //Os eventos primeiro: eles apontam para o caso
        if (id != null) {
            sql.update("delete from evento_do_caso where notificacao_id = ?", id);
            banco.apagar(Notificacao.class, id);
        }
        if (empresa != null) {
            banco.apagar(Empresa.class, empresa.getId());
        }
        if (escritorio != null) {
            banco.apagar(Escritorio.class, escritorio.getId());
        }
    }

    @Test
    void eventosAoMesmoTempoFormamUmaCorrenteSo() throws Exception {
        //Arrumar: um caso gravado de verdade
        escritorio = escritorios.save(new Escritorio("Escritório da Corrida"));
        empresa = empresas.save(new Empresa(escritorio, "99999009007000", "Padaria da Corrida"));
        id = notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto.", TipoDeDocumento.INTIMACAO,
                List.of(), List.of())).getId();
        Long escritorioId = escritorio.getId();

        //Agir: seis conversas esperam a largada juntas e gravam um evento cada uma no mesmo caso
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pessoas = Executors.newFixedThreadPool(AO_MESMO_TEMPO);
        List<Future<?>> pedidos = new ArrayList<>();
        for (int i = 0; i < AO_MESMO_TEMPO; i++) {
            String acao = "Evento ao mesmo tempo nº " + i;
            pedidos.add(pessoas.submit(() -> {
                largada.await();
                conversa.executeWithoutResult(estado -> historico.registrar(
                        notificacoes.buscar(id, escritorioId).orElseThrow(), "pessoa@escritorio.test", acao));
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> pedido : pedidos) {
            //get(): espera o pedido acabar e devolve o erro dele, se houve
            pedido.get();
        }
        pessoas.shutdown();

        //Conferir: os seis estão lá, com assinaturas diferentes, e a corrente confere de ponta a ponta
        List<EventoDoCaso> eventos = conversa.execute(estado -> historico.doCaso(id, escritorioId));
        assertThat(eventos).hasSize(AO_MESMO_TEMPO);
        assertThat(eventos).extracting(EventoDoCaso::getAssinatura).doesNotHaveDuplicates();
        assertThat(Trilha.conferir(eventos).integra()).isTrue();
    }
}
