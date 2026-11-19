package br.com.namtech.atalaia.historico;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.namtech.atalaia.notificacao.Notificacao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

//O historico dos casos: os controllers contam aqui o que fizeram, e a tela do caso le daqui.
//Service: o Spring cria um só e entrega a quem pedir no construtor, como faz com os repositorios
@Service
public class Historico {

    //Quem aparece no historico quando quem agiu foi o cliente, na pagina do link (ele nao tem conta)
    public static final String CLIENTE = "Cliente, pelo link";

    private final EventoDoCasoRepository eventos;
    private final Clock relogio;

    //A "mesa de trabalho" do JPA: serve aqui para trancar a linha do caso antes de encadear o evento
    @PersistenceContext
    private EntityManager mesa;

    //Sem public: o repositorio é do pacote; o Spring cria o servico pelo construtor mesmo assim
    Historico(EventoDoCasoRepository eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    //Num metodo Transactional (prazo, aviso, confirmação, resolver) ou dentro da transação do envio,
    //grava junto com a mudança do caso:
    //se o pedido falhar, o evento tambem nao fica
    public void registrar(Notificacao caso, String quem, String acao) {
        //Tranca a linha do caso: dois eventos ao mesmo tempo (o cliente confirma enquanto o escritório refaz o prazo)
        //leriam a mesma "ultima assinatura" e abririam duas correntes. Com a tranca, o segundo espera o primeiro gravar.
        //Quem chama ja costuma ter trancado (buscarParaAlterar); trancar de novo a mesma linha nao custa nada
        trancar(caso);
        String anterior = eventos.ultimaAssinatura(caso.getId(), caso.getEmpresa().getEscritorio().getId()).orElse("");
        //Em milissegundos: o banco guarda menos casas que o relogio de alguns servidores, e a assinatura
        //precisa sair igual quando for refeita com o que o banco devolve
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MILLIS);
        eventos.save(new EventoDoCaso(caso, quem, acao, agora, anterior));
    }

    //Quando alguém mexeu pela última vez no caso (a faxina usa para não anotar duas vezes)
    public Optional<Instant> ultimoMovimento(Long notificacaoId, Long escritorioId) {
        return eventos.ultimoMovimento(notificacaoId, escritorioId);
    }

    //Tranca a linha do caso até o fim da transação. Regra de quem chama: tranque o caso ANTES de qualquer outra
    //linha (aviso, evento). Todos na mesma ordem, dois pedidos nunca ficam esperando um pelo outro
    public void trancar(Notificacao caso) {
        mesa.lock(caso, LockModeType.PESSIMISTIC_WRITE);
    }

    public List<EventoDoCaso> doCaso(Long notificacaoId, Long escritorioId) {
        return eventos.doCaso(notificacaoId, escritorioId);
    }
}
