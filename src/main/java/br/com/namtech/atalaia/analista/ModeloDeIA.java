package br.com.namtech.atalaia.analista;

//A IA vista de dentro do Atalaia: recebe uma pergunta em texto e devolve a resposta em texto.
//É uma interface para o Analista nao depender de um fornecedor: hoje quem responde é o Gemini,
//e nos testes é uma resposta escrita à mao (sem internet, sem custo, sempre igual)
public interface ModeloDeIA {

    String responder(String pergunta);
}
