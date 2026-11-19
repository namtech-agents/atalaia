package br.com.namtech.atalaia.verificador;

import java.util.Locale;
import java.util.Map;

import jakarta.persistence.Embeddable;

//Um dado que o Analista tirou do documento: o nome do campo, o valor e o trecho literal de onde saiu.
//Exemplo: campo "prazo", valor "30 dias", trecho "Prazo: 30 dias a contar da ciencia."
//Embeddable: nao tem tabela nem id proprios para o JPA, é guardado como parte da Notificacao
@Embeddable
public record DadoExtraido(String campo, String valor, String trecho) {

    //Os campos que o pedido à IA lista (Analista.PEDIDO), com o nome que a tela mostra.
    //A IA escreve como o pedido manda: sem acento e com sublinhado (numero_do_processo).
    //Map.ofEntries: lista fixa, que ninguem consegue alterar depois
    public static final Map<String, String> CAMPOS_PEDIDOS = Map.ofEntries(
            Map.entry("valor", "Valor"),
            Map.entry("tributo", "Tributo"),
            Map.entry("numero_do_processo", "Número do processo"),
            Map.entry("remetente", "Remetente"),
            Map.entry("destinatario", "Destinatário"),
            Map.entry("prazo", "Prazo"));

    //O nome do campo como aparece na tela e nos motivos de revisao
    public String rotulo() {
        String nome = nome();
        if (nome.isEmpty()) {
            return "sem nome";
        }
        //"parte": notificação guardada antes de o pedido separar quem enviou de quem recebe
        if (nome.equals("parte")) {
            return "Parte";
        }
        //Fora da lista (a IA inventou um nome): aparece como veio, só sem o sublinhado
        return CAMPOS_PEDIDOS.getOrDefault(nome, campo.strip().replace('_', ' '));
    }

    //Se o campo é um dos que o pedido lista. O Verificador confere o valor e o trecho, mas um valor certo com nome
    //inventado apareceria na tela como dado conferido; por isso campo fora da lista vai para revisao
    public boolean conhecido() {
        return CAMPOS_PEDIDOS.containsKey(nome());
    }

    //A comparação ignora maiuscula e espaço nas pontas: " Prazo " e "prazo" sao o mesmo campo
    private String nome() {
        return campo == null ? "" : campo.strip().toLowerCase(Locale.ROOT);
    }
}
