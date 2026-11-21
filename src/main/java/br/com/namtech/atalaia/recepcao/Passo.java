package br.com.namtech.atalaia.recepcao;

import java.time.Instant;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.SituacaoDoAviso;
import br.com.namtech.atalaia.notificacao.Notificacao;

//O passo em que um caso aberto está, para a faixa de etapas no alto da tela do caso (#214).
//São quatro, na ordem em que o escritório trabalha: só o passo atual ganha o botão azul, e é isso que diz
//"o que fazer agora". Caso fechado não tem passo (a faixa aparece toda feita).
//A regra é a mesma da tela inicial (Etapa), olhada de dentro do caso: lá a pergunta é "de quem é a vez",
//aqui é "qual é o próximo passo"
public enum Passo {
    PRAZO("Prazo", "titulo-prazo"),
    AVISO("Avisar o cliente", "titulo-aviso"),
    CLIENTE("Cliente confirma", "titulo-aviso"),
    FECHAR("Fechar", "fechar");

    private final String nome;
    //O id do cartão onde o passo é feito: o botão do próximo passo leva até ele
    private final String ancora;

    Passo(String nome, String ancora) {
        this.nome = nome;
        this.ancora = ancora;
    }

    public String getNome() {
        return nome;
    }

    public String getAncora() {
        return ancora;
    }

    //"aviso" é o aviso que vale (o mais novo), ou null se o cliente ainda não foi avisado
    public static Passo de(Notificacao caso, AvisoAoCliente aviso, Instant agora) {
        if (caso.isResolvida()) {
            return null;
        }
        //Sem data limite não há o que avisar: o aviso leva a data junto
        if (caso.getDataLimite() == null) {
            return PRAZO;
        }
        //Sem aviso, ou com um que pede atenção (link vencido, data que mudou, cliente marcou a data errada): avisar de novo
        if (aviso == null || aviso.pedeAtencao(agora, caso.getDataLimite())) {
            return AVISO;
        }
        return aviso.situacao(agora) == SituacaoDoAviso.CONFIRMADO ? FECHAR : CLIENTE;
    }
}
