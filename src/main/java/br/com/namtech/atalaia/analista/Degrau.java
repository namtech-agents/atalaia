package br.com.namtech.atalaia.analista;

//O degrau da notificação (US28): a gravidade, do mais leve ao mais grave. A ordem desta lista É a escada:
//o degrau seguinte é o que costuma vir se ninguém agir. A IA sugere; o contador corrige com um clique
public enum Degrau {
    AVISO("Aviso"),
    PEDIDO_DE_DOCUMENTO("Pedido de documento"),
    INTIMACAO("Intimação"),
    AUTO_DE_INFRACAO("Auto de infração"),
    COBRANCA("Cobrança");

    //O nome como aparece na tela, com acento
    private final String nome;

    Degrau(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    //O degrau de cima, ou null quando este já é o mais alto
    public Degrau seguinte() {
        int proximo = ordinal() + 1;
        return proximo < values().length ? values()[proximo] : null;
    }

    //O degrau pelo nome que a IA escreveu, ou null se ela não mandou um dos cinco.
    //Não falha: um degrau errado não pode mandar para revisão um documento que foi lido certo
    static Degrau doNome(String nome) {
        for (Degrau degrau : values()) {
            if (degrau.name().equals(nome)) {
                return degrau;
            }
        }
        return null;
    }
}
