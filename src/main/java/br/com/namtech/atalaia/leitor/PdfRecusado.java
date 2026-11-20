package br.com.namtech.atalaia.leitor;

import java.io.IOException;

//O PDF abriu, mas o Leitor se recusa a ir até o fim: páginas demais, conteúdo que incha ou leitura demorada.
//A mensagem já é a frase que a pessoa vê na tela. É um IOException para quem chama poder tratar junto
//com os outros problemas de leitura, ou separado, mostrando a mensagem
public class PdfRecusado extends IOException {

    public PdfRecusado(String mensagem) {
        super(mensagem);
    }
}
