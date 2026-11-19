package br.com.namtech.atalaia.leitor;

//O que o leitor devolve: o texto do PDF, o aviso se precisa de revisao humana e, só quando o PDF
//trazia scripts, uma copia sem eles para a tela mostrar (null = o original pode ser mostrado como esta)
public record ResultadoLeitura(String texto, boolean precisaDeRevisao, byte[] semScripts) {
}
