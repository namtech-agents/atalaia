package br.com.namtech.atalaia.historico;

import java.util.Comparator;
import java.util.List;

//A conferência da trilha de um caso (US27): refaz a assinatura de cada evento, na ordem em que foram gravados,
//e diz se todas conferem. "quebra" é o primeiro evento que não confere (null se a trilha está inteira);
//"selo" é a assinatura do último evento, que vai no PDF: guardar o PDF guarda o selo
public record Trilha(EventoDoCaso quebra, String selo, boolean temAssinatura) {

    public boolean integra() {
        return quebra == null;
    }

    //Os eventos de antes da trilha (sem assinatura, V14) ficam fora da conta. Depois do primeiro assinado,
    //um evento sem assinatura também é quebra: alguém apagou a assinatura dele
    public static Trilha conferir(List<EventoDoCaso> eventos) {
        //Na ordem em que foram gravados (o id). A tela mostra por data, mas a corrente segue a gravação
        List<EventoDoCaso> naOrdem = eventos.stream().sorted(Comparator.comparing(EventoDoCaso::getId)).toList();
        String anterior = "";
        boolean comecou = false;
        for (EventoDoCaso evento : naOrdem) {
            if (!comecou && evento.getAssinatura() == null) {
                continue;
            }
            comecou = true;
            if (!evento.confere(anterior)) {
                return new Trilha(evento, null, true);
            }
            anterior = evento.getAssinatura();
        }
        return new Trilha(null, comecou ? anterior : null, comecou);
    }
}
