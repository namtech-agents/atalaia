package br.com.namtech.atalaia.aviso;

//Em que pé esta um aviso ao cliente
public enum SituacaoDoAviso {
    AGUARDANDO("Aguardando o cliente"),
    CONFIRMADO("Cliente confirmou"),
    //O escritório fez um aviso novo no lugar deste
    SUBSTITUIDO("Substituído por um aviso mais novo"),
    VENCIDO("Link vencido sem confirmação");

    //O nome como aparece na tela
    private final String nome;

    SituacaoDoAviso(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
