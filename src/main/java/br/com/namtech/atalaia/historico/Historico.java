package br.com.namtech.atalaia.historico;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.namtech.atalaia.notificacao.Notificacao;

//O historico dos casos: os controllers contam aqui o que fizeram, e a tela do caso le daqui.
//Service: o Spring cria um só e entrega a quem pedir no construtor, como faz com os repositorios
@Service
public class Historico {

    //Quem aparece no historico quando quem agiu foi o cliente, na pagina do link (ele nao tem conta)
    public static final String CLIENTE = "Cliente, pelo link";

    private final EventoDoCasoRepository eventos;
    private final Clock relogio;

    public Historico(EventoDoCasoRepository eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    //Num metodo Transactional (prazo, aviso, confirmação, resolver), grava junto com a mudança do caso:
    //se o pedido falhar, o evento tambem nao fica
    public void registrar(Notificacao caso, String quem, String acao) {
        eventos.save(new EventoDoCaso(caso, quem, acao, Instant.now(relogio)));
    }

    public List<EventoDoCaso> doCaso(Long notificacaoId, Long escritorioId) {
        return eventos.doCaso(notificacaoId, escritorioId);
    }
}
