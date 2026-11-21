package br.com.namtech.atalaia.painel;

import java.time.Instant;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;
import br.com.namtech.atalaia.recepcao.Passo;

//Em que etapa esta um caso aberto, pelo que importa a quem abre a tela inicial: de quem é o proximo passo.
//Os grupos da tela seguem esta ordem: o que o escritorio tem que fazer vem primeiro
public enum Etapa {
    PRECISA_DE_GENTE,
    ESPERANDO_O_CLIENTE,
    PRONTO_PARA_FECHAR;

    //"aviso" é o aviso que vale (o mais novo) do caso, ou null se o cliente ainda nao foi avisado
    //Sai do passo do caso (Passo, #214): prazo e aviso são do escritório, "cliente confirma" é esperar,
    //e "fechar" é o pronto para fechar. Uma regra só para a lista e para a tela do caso
    public static Etapa de(NotificacaoNaLista linha, AvisoAoCliente aviso, Instant agora) {
        return switch (Passo.de(false, linha.dataLimite(), aviso, agora)) {
            case PRAZO, AVISO -> PRECISA_DE_GENTE;
            case CLIENTE -> ESPERANDO_O_CLIENTE;
            case FECHAR -> PRONTO_PARA_FECHAR;
        };
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
