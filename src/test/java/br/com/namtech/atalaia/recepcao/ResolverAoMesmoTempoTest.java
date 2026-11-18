package br.com.namtech.atalaia.recepcao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Duas pessoas mandando resolver a mesma notificação ao mesmo tempo.
//Este teste nao tem Transactional: as duas "pessoas" sao conversas separadas com o banco, e cada uma grava de verdade.
//Por isso ele mesmo apaga o que criou, no fim
@SpringBootTest
class ResolverAoMesmoTempoTest {

    private MockMvc navegador;

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    private Escritorio escritorio;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    //TransactionTemplate: abre uma conversa com o banco, roda o que estiver dentro e grava no fim
    @Autowired
    private TransactionTemplate conversa;

    @MockitoBean
    private ModeloDeIA ia;

    private Empresa empresa;
    private Long id;

    @AfterEach
    void apagaOQueCriou() {
        //Só apaga o que chegou a ser criado: se o teste quebrou antes, o erro que aparece é o dele, nao o daqui
        if (id != null) {
            notificacoes.deleteById(id);
        }
        if (empresa != null) {
            empresas.delete(empresa);
        }
        if (escritorio != null) {
            escritorios.delete(escritorio);
        }
    }

    @Test
    void segundoPedidoDeResolverEsperaOPrimeiroENaoTrocaOQueFoiRegistrado() throws Exception {
        //Arrumar: uma notificação aberta, e uma segunda linha de execução para fazer o papel da outra pessoa
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        empresa = empresas.save(new Empresa(escritorio, "99999009000281", "Empresa de Teste"));
        id = notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "texto",
                TipoDeDocumento.COBRANCA, List.of(), List.of())).getId();
        ExecutorService outraPessoa = Executors.newSingleThreadExecutor();
        //Avisa quando a outra pessoa começou de verdade (CompletableFuture: um aviso que alguem da e outro espera)
        CompletableFuture<Void> outraPessoaComecou = new CompletableFuture<>();

        Future<ResultActions> segundoPedido;
        try {
            segundoPedido = conversa.execute(aberta -> {
                //Agir: a primeira pessoa pega a notificação para alterar e, antes de ela gravar, a segunda manda resolver
                Notificacao daPrimeira = notificacoes.buscarParaAlterar(id, escritorio.getId()).orElseThrow();
                Future<ResultActions> pedido = outraPessoa.submit(() -> {
                    outraPessoaComecou.complete(null);
                    return navegador.perform(post("/notificacoes/" + id + "/resolver")
                            .param("desfecho", "MULTA_CHEIA").param("observacao", "Segunda observação."));
                });
                //Espera a outra pessoa começar e da um tempo para o pedido dela chegar no banco.
                //Sem a tranca, nesse tempo ela gravaria MULTA_CHEIA; com a tranca, fica parada e nao responde.
                //(Nao aumentar os 300 ms: o banco de teste desiste de esperar uma tranca depois de uns 2 segundos)
                outraPessoaComecou.orTimeout(5, TimeUnit.SECONDS).join();
                assertThatThrownBy(() -> pedido.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                daPrimeira.resolver(Desfecho.ARQUIVADO, "Primeira observação.");
                return pedido;
            });
            //A conversa da primeira pessoa terminou e gravou: agora o segundo pedido anda
            segundoPedido.get(5, TimeUnit.SECONDS).andExpect(status().is3xxRedirection());
        } finally {
            outraPessoa.shutdownNow();
        }

        //Conferir (é esta a prova da tranca): o segundo encontrou o caso ja resolvido e nao trocou nada
        Notificacao guardada = notificacoes.findById(id).orElseThrow();
        assertThat(guardada.getDesfecho()).isEqualTo(Desfecho.ARQUIVADO);
        assertThat(guardada.getObservacao()).isEqualTo("Primeira observação.");
    }
}
