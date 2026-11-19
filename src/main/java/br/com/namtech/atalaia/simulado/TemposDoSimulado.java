package br.com.namtech.atalaia.simulado;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.notificacao.Notificacao;

//Os tempos de um simulado (US30), contados da chegada: até alguém abrir, até gerar o primeiro aviso ao cliente e até
//fechar. "Fechar" é o fechamento que vale agora: reabrir apaga a data, e o tempo passa a contar o próximo fechamento.
//"Responsável" ainda não existe no Atalaia (US02, fora do MVP): a tela diz isso em vez de inventar um tempo
public record TemposDoSimulado(Long id, String empresa, Instant chegou, Instant abriu, Instant avisou, Instant fechou) {

    //avisos: os avisos do caso, do mais novo para o mais antigo (AvisoAoClienteRepository.doCaso). Conta o primeiro
    public static TemposDoSimulado de(Notificacao simulado, List<AvisoAoCliente> avisos) {
        Instant primeiroAviso = avisos.isEmpty() ? null : avisos.get(avisos.size() - 1).getCriadoEm();
        return new TemposDoSimulado(simulado.getId(), simulado.getEmpresa().getNome(), simulado.getRecebidaEm(),
                simulado.getAbertaEm(), primeiroAviso, simulado.getResolvidaEm());
    }

    //O simulado "termina" quando o cliente foi avisado ou o caso foi fechado. Só então o caso conta que era um teste:
    //antes disso, a equipe trabalha nele como num caso de verdade
    public boolean terminou() {
        return avisou != null || fechou != null;
    }

    public String getChegouPorExtenso() {
        return Notificacao.DIA_E_HORA.format(chegou);
    }

    public String getAteAbrir() {
        return depoisDaChegada(abriu);
    }

    public String getAteAvisar() {
        return depoisDaChegada(avisou);
    }

    public String getAteFechar() {
        return depoisDaChegada(fechou);
    }

    private String depoisDaChegada(Instant quando) {
        return quando == null ? "ainda não" : duracao(Duration.between(chegou, quando));
    }

    //"menos de 1 min", "12 min", "2 h 5 min", "3 dias e 4 h": o tamanho de tempo que importa para quem lê
    static String duracao(Duration tempo) {
        //Negativo não acontece (todo marco vem depois da chegada); o max só evita "-1 min" se o relógio do servidor
        //for acertado para trás no meio de um simulado
        long minutos = Math.max(tempo.toMinutes(), 0);
        if (minutos < 1) {
            return "menos de 1 min";
        }
        if (minutos < 60) {
            return minutos + " min";
        }
        long horas = minutos / 60;
        if (horas < 24) {
            return horas + " h" + (minutos % 60 == 0 ? "" : " " + minutos % 60 + " min");
        }
        long dias = horas / 24;
        return dias + (dias == 1 ? " dia" : " dias") + (horas % 24 == 0 ? "" : " e " + horas % 24 + " h");
    }
}
