package br.com.namtech.atalaia.analista;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//Diz ao Spring qual IA entregar para quem pedir um ModeloDeIA
@Configuration
public class ConfiguracaoDaIA {

    //A chave vem da variavel de ambiente GEMINI_API_KEY. O ":" depois do nome quer dizer "se nao existir, use vazio"
    @Bean
    ModeloDeIA modeloDeIA(@Value("${GEMINI_API_KEY:}") String chave) {
        if (chave.isBlank()) {
            //Sem chave o Atalaia sobe do mesmo jeito: a IA "falha" e o Analista manda cada documento para uma pessoa
            return pergunta -> {
                throw new IllegalStateException("Falta a variavel de ambiente GEMINI_API_KEY");
            };
        }
        return new Gemini(chave);
    }
}
