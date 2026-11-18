package br.com.namtech.atalaia.verificador;

import jakarta.persistence.Embeddable;

//Um dado que o Analista tirou do documento: o nome do campo, o valor e o trecho literal de onde saiu.
//Exemplo: campo "prazo", valor "30 dias", trecho "Prazo: 30 dias a contar da ciencia."
//Embeddable: nao tem tabela nem id proprios para o JPA, é guardado como parte da Notificacao
@Embeddable
public record DadoExtraido(String campo, String valor, String trecho) {
}
