package br.com.namtech.atalaia.analista;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

//Teto de chamadas à IA ao mesmo tempo (achado do pentest, issue #144). Cada chamada segura uma linha do servidor
//por até 60 s (2 tentativas de 30 s no Gemini): muitas juntas ocupariam todas as linhas e o Atalaia pararia para
//todo mundo. Envolve a IA de verdade (ConfiguracaoDaIA) e, para quem usa, continua sendo um ModeloDeIA
public class TetoDeChamadas implements ModeloDeIA {

    private final ModeloDeIA ia;

    //Semaphore: um balcão com N vagas. Quem chega pega uma vaga; sem vaga, espera ou desiste
    private final Semaphore vagas;

    private final Duration espera;

    public TetoDeChamadas(ModeloDeIA ia, int maximoAoMesmoTempo, Duration espera) {
        this.ia = ia;
        this.vagas = new Semaphore(maximoAoMesmoTempo);
        this.espera = espera;
    }

    @Override
    public String responder(String pergunta) {
        boolean pegou;
        try {
            pegou = vagas.tryAcquire(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException erro) {
            //O servidor está desligando: devolve o aviso de interrupção e desiste
            Thread.currentThread().interrupt();
            pegou = false;
        }
        if (!pegou) {
            //O Analista trata como "a IA não respondeu": o caso não some, vai para uma pessoa conferir
            throw new IllegalStateException("A IA está ocupada com outras análises");
        }
        //finally: a vaga volta mesmo se a IA falhar
        try {
            return ia.responder(pergunta);
        } finally {
            vagas.release();
        }
    }
}
