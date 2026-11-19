package br.com.namtech.atalaia.analista;

import java.util.Locale;

//O degrau da notificação (US28): a gravidade, do mais leve ao mais grave. A ordem desta lista É a escala do Atalaia:
//uma escala geral de gravidade, não a regra de nenhum órgão. A IA sugere; o contador corrige com um clique.
//Os nomes não repetem os do tipo (TipoDeDocumento): "Intimação" num cartão e "Intimação" no outro confundiam
public enum Degrau {
    AVISO("Aviso"),
    PEDIDO_DE_DOCUMENTO("Pedido de documento"),
    EXIGENCIA_SOB_PENA("Exigência sob pena"),
    AUTO_DE_INFRACAO("Auto de infração"),
    DEBITO_COBRADO("Débito cobrado");

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

    //O degrau pelo nome que a IA escreveu, ou null se ela não mandou um dos cinco. Aceita minúscula e espaço
    //nas pontas ("aviso", " AVISO"). Não falha: um degrau errado não manda para revisão um documento lido certo
    static Degrau doNome(String nome) {
        String limpo = nome == null ? "" : nome.strip().toUpperCase(Locale.ROOT);
        for (Degrau degrau : values()) {
            if (degrau.name().equals(limpo)) {
                return degrau;
            }
        }
        return null;
    }
}
