package br.com.namtech.atalaia.notificacao;

import java.time.Instant;

import br.com.namtech.atalaia.analista.TipoDeDocumento;

//Uma linha da lista de notificações: só o que a lista mostra.
//Buscar a Notificacao inteira traria junto o PDF e o texto de cada uma, que a lista nao usa
public record NotificacaoNaLista(Long id, String empresa, TipoDeDocumento tipo, boolean precisaDeRevisao,
        Instant recebidaEm) {

    //A data como aparece na tela
    public String getRecebida() {
        return Notificacao.DIA_E_HORA.format(recebidaEm);
    }
}
