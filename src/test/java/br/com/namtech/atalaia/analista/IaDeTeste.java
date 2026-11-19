package br.com.namtech.atalaia.analista;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

//A IA de mentira dos testes que enviam PDF. Desde a segunda passada (US25) o envio faz duas perguntas à IA:
//a leitura e o "falta algo ou há conflito?". Este ajudante responde cada uma com o que cabe a ela
public final class IaDeTeste {

    //A resposta da segunda passada quando o documento está em ordem
    public static final String SEM_DUVIDA = "{\"duvida\": false, \"trecho\": \"\", \"porque\": \"\"}";

    private IaDeTeste() {
    }

    //A IA responde "leitura" à primeira pergunta e "sem dúvida" à segunda
    public static void responde(ModeloDeIA ia, String leitura) {
        responde(ia, leitura, SEM_DUVIDA);
    }

    public static void responde(ModeloDeIA ia, String leitura, String duvida) {
        when(ia.responder(anyString())).thenAnswer(chamada -> ehSegundaPassada(chamada.getArgument(0)) ? duvida : leitura);
    }

    //Reconhece a segunda pergunta pelo começo do pedido (o resto muda com o documento)
    public static boolean ehSegundaPassada(String pergunta) {
        return pergunta.startsWith(Analista.PEDIDO_DA_DUVIDA.substring(0, 60));
    }
}
