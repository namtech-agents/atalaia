package br.com.namtech.atalaia.leitor;

import java.io.IOException;

//O PDF abriu, mas tem mais paginas do que o Leitor aceita. É um IOException para quem chama poder
//tratar junto com os outros problemas de leitura, ou separado, com uma mensagem propria
public class PaginasDemais extends IOException {

    public PaginasDemais() {
        super("O PDF passa de " + LeitorPdf.MAXIMO_DE_PAGINAS + " páginas");
    }
}
