package br.com.namtech.atalaia.seguranca;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

//A conta de quantas tentativas cada endereço (IP) fez na janela de 15 minutos. Quem usa é a TravaDeTentativas.
//Fica na memória do servidor, não no banco: se o Atalaia reiniciar, a conta zera. Para um servidor só, basta;
//com mais de um servidor, cada um teria a sua conta e o limite valeria por servidor
@Component
public class LimiteDeTentativas {

    static final Duration JANELA = Duration.ofMinutes(15);

    //Teto de endereços guardados ao mesmo tempo. Sem teto, um ataque vindo de muitos endereços encheria a memória
    static final int TETO_PADRAO = 10_000;

    //Com a memória cheia, procurar endereços vencidos no máximo uma vez por minuto.
    //Procurar a cada pedido deixaria cada tentativa mais lenta justamente durante o ataque
    private static final Duration ESPERA_ENTRE_LIMPEZAS = Duration.ofMinutes(1);

    //Quando a janela de um endereço começou e quantas tentativas ele já fez nela
    private record Conta(Instant inicio, int vezes) {
    }

    private final int maximo;
    private final int teto;
    private final Clock relogio;
    private final Map<String, Conta> contas = new ConcurrentHashMap<>();
    private volatile Instant proximaLimpeza = Instant.MIN;

    //O máximo vem da configuração para os testes poderem trocar; no ar fica o padrão, 10
    @Autowired
    public LimiteDeTentativas(@Value("${atalaia.tentativas.maximo:10}") int maximo, Clock relogio) {
        this(maximo, TETO_PADRAO, relogio);
    }

    //Este recebe também o teto, para o teste conferir a memória cheia sem criar 10 mil endereços
    LimiteDeTentativas(int maximo, int teto, Clock relogio) {
        this.maximo = maximo;
        this.teto = teto;
        this.relogio = relogio;
    }

    //Pede uma vaga ANTES de conferir a senha. true = pode seguir (e a tentativa já foi contada); false = travado.
    //Olhar e contar acontecem juntos, num só compute: se fossem dois passos, 30 pedidos ao mesmo tempo
    //olhariam a conta antes de qualquer um contar, e os 30 passariam (achado da revisão do Hefesto)
    public boolean reservar(String endereco) {
        String chave = chave(endereco);
        if (contas.size() >= teto && !contas.containsKey(chave) && !abrirEspaco()) {
            //Memória cheia de endereços dentro da janela: endereço novo espera, os que já estão guardados seguem
            return false;
        }
        boolean[] liberado = {false};
        contas.compute(chave, (k, conta) -> {
            if (conta == null || !aberta(conta)) {
                liberado[0] = true;
                return new Conta(relogio.instant(), 1);
            }
            if (conta.vezes() >= maximo) {
                return conta;
            }
            liberado[0] = true;
            return new Conta(conta.inicio(), conta.vezes() + 1);
        });
        return liberado[0];
    }

    //Entrou com a senha certa: a vaga volta. Assim o escritório inteiro pode entrar pela mesma internet
    public void devolver(String endereco) {
        contas.computeIfPresent(chave(endereco),
                (k, conta) -> conta.vezes() <= 1 ? null : new Conta(conta.inicio(), conta.vezes() - 1));
    }

    //Tira da memória quem já saiu da janela. Devolve se sobrou espaço
    private boolean abrirEspaco() {
        Instant agora = relogio.instant();
        if (agora.isBefore(proximaLimpeza)) {
            return false;
        }
        proximaLimpeza = agora.plus(ESPERA_ENTRE_LIMPEZAS);
        contas.values().removeIf(conta -> !aberta(conta));
        return contas.size() < teto;
    }

    //IPv4 conta por endereço. IPv6 conta pelo bloco /64 (os primeiros 8 bytes): quem tem um bloco desses
    //troca de endereço à vontade, e contar um por um deixaria escapar da trava
    static String chave(String endereco) {
        if (!endereco.contains(":")) {
            return endereco;
        }
        try {
            //Com um endereço escrito em números, getByName só converte; não consulta a internet (DNS)
            byte[] bytes = InetAddress.getByName(endereco).getAddress();
            if (bytes.length == 16) {
                return HexFormat.of().formatHex(bytes, 0, 8) + "/64";
            }
        } catch (UnknownHostException erro) {
            //Não é endereço válido: conta pelo texto mesmo
        }
        return endereco;
    }

    //A janela ainda não acabou
    private boolean aberta(Conta conta) {
        return relogio.instant().isBefore(conta.inicio().plus(JANELA));
    }
}
