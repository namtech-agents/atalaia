package br.com.namtech.atalaia.recepcao;

import java.time.Instant;
import java.time.LocalDate;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.SituacaoDoAviso;
import br.com.namtech.atalaia.notificacao.Notificacao;

//O passo em que um caso aberto está, para a faixa de etapas no alto da tela do caso (#214).
//São quatro, na ordem em que o escritório trabalha: só o passo atual ganha o botão azul, e é isso que diz
//"o que fazer agora". Caso fechado não tem passo (a faixa aparece toda feita).
//É A regra do andamento do caso: a tela inicial (Etapa) também sai daqui, só agrupando os passos por
//"de quem é a vez". Uma regra só, para a lista e o caso nunca discordarem
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
        return de(caso.isResolvida(), caso.getDataLimite(), aviso, agora);
    }

    //A mesma regra para quem tem só os dados da linha da lista (Etapa)
    public static Passo de(boolean resolvida, LocalDate dataLimite, AvisoAoCliente aviso, Instant agora) {
        if (resolvida) {
            return null;
        }
        //Sem data limite não há o que avisar: o aviso leva a data junto
        if (dataLimite == null) {
            return PRAZO;
        }
        //Sem aviso, ou com um que pede atenção (link vencido, data que mudou, cliente marcou a data errada): avisar de novo
        if (aviso == null || aviso.pedeAtencao(agora, dataLimite)) {
            return AVISO;
        }
        return aviso.situacao(agora) == SituacaoDoAviso.CONFIRMADO ? FECHAR : CLIENTE;
    }
}
