package br.com.namtech.atalaia.verificador;

import jakarta.persistence.Embeddable;

//Um dado que o Analista tirou do documento: o nome do campo, o valor e o trecho literal de onde saiu.
//Exemplo: campo "prazo", valor "30 dias", trecho "Prazo: 30 dias a contar da ciencia."
//Embeddable: nao tem tabela nem id proprios para o JPA, é guardado como parte da Notificacao
@Embeddable
public record DadoExtraido(String campo, String valor, String trecho) {

    //O nome do campo como aparece na tela e nos motivos de revisao.
    //A IA escreve como o pedido manda: sem acento e com sublinhado (numero_do_processo)
    public String rotulo() {
        return switch (campo) {
            case null -> "sem nome";
            case "valor" -> "Valor";
            case "tributo" -> "Tributo";
            case "numero_do_processo" -> "Número do processo";
            case "remetente" -> "Remetente";
            case "destinatario" -> "Destinatário";
            case "prazo" -> "Prazo";
            //"parte": notificação guardada antes de o pedido separar quem enviou de quem recebe
            case "parte" -> "Parte";
            //Fora da lista (a IA inventou um nome): aparece como veio, só sem o sublinhado
            default -> campo.replace('_', ' ');
        };
    }
}
