package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import br.com.namtech.atalaia.analista.Degrau;
import br.com.namtech.atalaia.analista.TipoDeDocumento;

//Uma linha da lista de notificações: só o que a lista mostra.
//Buscar a Notificacao inteira traria junto o PDF e o texto de cada uma, que a lista nao usa.
//empresaId: o filtro por cliente da tela inicial separa as linhas por ele
public record NotificacaoNaLista(Long id, Long empresaId, String empresa, TipoDeDocumento tipo, Degrau degrau,
        boolean precisaDeRevisao,
        Instant recebidaEm, Desfecho desfecho, Instant resolvidaEm, LocalDate dataLimite) {

    //Resolvida é a que ja tem a data da resolução, igual na Notificacao
    public boolean resolvida() {
        return resolvidaEm != null;
    }

    //A data que a linha mostra, como aparece na tela: a de quando foi resolvida ou, no caso aberto, a de quando chegou
    public String getQuando() {
        return Notificacao.DIA_E_HORA.format(resolvida() ? resolvidaEm : recebidaEm);
    }

    //Com 3 dias ou menos para vencer (ou ja vencido), o prazo aparece em vermelho na lista
    static final int DIAS_DE_ALERTA = 3;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    //Só caso aberto com data limite mostra prazo na lista
    public boolean temPrazo() {
        return dataLimite != null && !resolvida();
    }

    //O prazo como a lista mostra. "hoje" vem de fora para o teste poder parar o tempo.
    //O texto diz a situação por extenso: quem nao distingue a cor nao perde a informação
    public String prazo(LocalDate hoje) {
        return prazo(dataLimite, hoje);
    }

    //A mesma frase para quem tem só a data: a tela do caso usa no painel Situação (#214)
    public static String prazo(LocalDate dataLimite, LocalDate hoje) {
        long dias = ChronoUnit.DAYS.between(hoje, dataLimite);
        if (dias < 0) {
            return "Venceu em " + DIA.format(dataLimite);
        }
        if (dias == 0) {
            return "Vence hoje";
        }
        if (dias == 1) {
            return "Vence amanhã";
        }
        return "Vence em " + dias + " dias, " + DIA.format(dataLimite);
    }

    //Ate 7 dias (e fora do alerta), o prazo fica no nivel do meio: é a semana que vem
    static final int DIAS_DE_PERTO = 7;

    public boolean prazoApertado(LocalDate hoje) {
        return prazoApertado(dataLimite, hoje);
    }

    public static boolean prazoApertado(LocalDate dataLimite, LocalDate hoje) {
        return ChronoUnit.DAYS.between(hoje, dataLimite) <= DIAS_DE_ALERTA;
    }

    //Tres niveis de cor: apertado (vermelho), perto (amarelo) e com folga (sem cor). Cada caso fica em um só
    public boolean prazoPerto(LocalDate hoje) {
        return prazoPerto(dataLimite, hoje);
    }

    public static boolean prazoPerto(LocalDate dataLimite, LocalDate hoje) {
        return !prazoApertado(dataLimite, hoje) && ChronoUnit.DAYS.between(hoje, dataLimite) <= DIAS_DE_PERTO;
    }
}
