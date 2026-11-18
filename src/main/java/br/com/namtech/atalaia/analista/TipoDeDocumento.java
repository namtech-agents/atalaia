package br.com.namtech.atalaia.analista;

//Os cinco tipos em que o Analista classifica um documento
public enum TipoDeDocumento {
    CITACAO("Citação"),
    INTIMACAO("Intimação"),
    COBRANCA("Cobrança"),
    EXCLUSAO("Exclusão"),
    INFORMATIVO("Informativo");

    //O nome como aparece na tela, com acento
    private final String nome;

    TipoDeDocumento(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
