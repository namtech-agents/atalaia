package br.com.namtech.atalaia.analista;

import java.time.Duration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

//O Gemini (IA do Google) respondendo pelo LangChain4j
public class Gemini implements ModeloDeIA {

    //O nome do modelo fica num lugar só: quando o Google trocar, muda aqui
    private static final String MODELO = "gemini-3.5-flash-lite";

    private final ChatModel modelo;

    //A chave nunca fica no código: quem cria o Gemini traz a chave de uma variavel de ambiente
    public Gemini(String chave) {
        this.modelo = GoogleAiGeminiChatModel.builder()
                .apiKey(chave)
                .modelName(MODELO)
                //Obriga a resposta a ser um JSON, sem texto em volta
                .responseFormat(ResponseFormat.JSON)
                //Temperatura 0: a IA escolhe sempre a resposta mais provável, em vez de sortear entre as parecidas.
                //Para copiar dados de um documento, criatividade só atrapalha. O teste de variação (US29) achou o
                //mesmo documento, com os parágrafos em outra ordem, voltando com um campo a mais e o prazo escrito
                //de outro jeito
                .temperature(0.0)
                //Quem enviou o PDF esta esperando a tela: cada tentativa espera no maximo 30 segundos,
                //e sao no maximo 2 tentativas antes de desistir (e o caso ir para uma pessoa)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .build();
    }

    @Override
    public String responder(String pergunta) {
        return modelo.chat(pergunta);
    }
}
