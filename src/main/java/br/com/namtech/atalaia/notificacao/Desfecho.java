package br.com.namtech.atalaia.notificacao;

//Como o caso terminou. A pessoa escolhe um ao marcar a notificação como resolvida
public enum Desfecho {
    ARQUIVADO("Arquivado"),
    MULTA_REDUZIDA("Multa reduzida"),
    MULTA_CHEIA("Multa cheia"),
    OUTRO("Outro");

    //O nome como aparece na tela
    private final String nome;

    Desfecho(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
