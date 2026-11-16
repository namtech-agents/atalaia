package br.com.namtech.atalaia.leitor;

//O que o leitor devolve: o texto do PDF e o aviso se precisa de revisao humana
public record ResultadoLeitura(String texto, boolean precisaDeRevisao) {
}
