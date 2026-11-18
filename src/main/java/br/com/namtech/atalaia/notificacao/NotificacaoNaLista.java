package br.com.namtech.atalaia.notificacao;

import java.time.Instant;

import br.com.namtech.atalaia.analista.TipoDeDocumento;

//Uma linha da lista de notificações: só o que a lista mostra.
//Buscar a Notificacao inteira traria junto o PDF e o texto de cada uma, que a lista nao usa
public record NotificacaoNaLista(Long id, String empresa, TipoDeDocumento tipo, boolean precisaDeRevisao,
        Instant recebidaEm, Desfecho desfecho, Instant resolvidaEm) {

    //Resolvida é a que ja tem a data da resolução, igual na Notificacao
    public boolean resolvida() {
        return resolvidaEm != null;
    }

    //A data que a linha mostra, como aparece na tela: a de quando foi resolvida ou, no caso aberto, a de quando chegou
    public String getQuando() {
        return Notificacao.DIA_E_HORA.format(resolvida() ? resolvidaEm : recebidaEm);
    }
}
