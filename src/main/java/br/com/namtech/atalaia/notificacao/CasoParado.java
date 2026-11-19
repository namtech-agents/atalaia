package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.com.namtech.atalaia.Datas;
import br.com.namtech.atalaia.analista.TipoDeDocumento;

//Uma linha da faxina de sexta: um caso aberto e a ultima vez que alguem mexeu nele (o evento mais novo do historico,
//ou a chegada, se o caso nao tiver evento). NotificacaoRepository.parados monta cada linha
public record CasoParado(Long id, String empresa, TipoDeDocumento tipo, LocalDate dataLimite, Instant ultimoMovimento) {

    //Vencido: a data limite ja passou e o caso continua aberto
    public boolean vencido(LocalDate hoje) {
        return dataLimite != null && dataLimite.isBefore(hoje);
    }

    //A mesma regra da lista da tela inicial: até 3 dias para vencer, o prazo aparece em vermelho
    public boolean prazoApertado(LocalDate hoje) {
        return dataLimite != null && ChronoUnit.DAYS.between(hoje, dataLimite) <= NotificacaoNaLista.DIAS_DE_ALERTA;
    }

    //Ha quantos dias inteiros ninguem mexe no caso, para a linha dizer "parado há N dias"
    public long diasParado(Instant agora) {
        return ChronoUnit.DAYS.between(ultimoMovimento, agora);
    }

    public String getDataLimitePorExtenso() {
        return dataLimite == null ? null : Datas.DIA.format(dataLimite);
    }
}
