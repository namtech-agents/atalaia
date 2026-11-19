package br.com.namtech.atalaia.painel;

import java.time.Instant;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.SituacaoDoAviso;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;

//Em que etapa esta um caso aberto, pelo que importa a quem abre a tela inicial: de quem é o proximo passo.
//Os grupos da tela seguem esta ordem: o que o escritorio tem que fazer vem primeiro
public enum Etapa {
    PRECISA_DE_GENTE,
    ESPERANDO_O_CLIENTE,
    PRONTO_PARA_FECHAR;

    //"aviso" é o aviso que vale (o mais novo) do caso, ou null se o cliente ainda nao foi avisado
    public static Etapa de(NotificacaoNaLista linha, AvisoAoCliente aviso, Instant agora) {
        //Sem aviso, ou com um aviso que pede atenção (link vencido, data errada, data que mudou): a vez é do escritorio
        if (aviso == null || aviso.pedeAtencao(agora, linha.dataLimite())) {
            return PRECISA_DE_GENTE;
        }
        //Sobram o aviso valendo e o confirmado com a data certa. (Substituido nunca é o mais novo)
        return aviso.situacao(agora) == SituacaoDoAviso.CONFIRMADO ? PRONTO_PARA_FECHAR : ESPERANDO_O_CLIENTE;
    }

    //O que falta fazer num caso sem aviso, para a etiqueta da lista; null quando ja ha aviso, porque a etiqueta
    //do proprio aviso explica a situação. O sinal de revisao da IA nunca é apagado: depois que alguem calculou
    //o prazo, a leitura ja foi olhada, entao ele só conta enquanto o caso nao tem prazo
    public static String oQueFalta(NotificacaoNaLista linha, AvisoAoCliente aviso) {
        if (aviso != null) {
            return null;
        }
        if (linha.dataLimite() == null) {
            return linha.precisaDeRevisao() ? "Conferir a leitura" : "Calcular o prazo";
        }
        return "Avisar o cliente";
    }
}
