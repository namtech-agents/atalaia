package br.com.namtech.atalaia.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

//A conta de quantas tentativas cada endereço (IP) fez na janela de 15 minutos. Quem usa é a TravaDeTentativas.
//Fica na memória do servidor, não no banco: se o Atalaia reiniciar, a conta zera. Para um servidor só, basta;
//com mais de um servidor, cada um teria a sua conta e o limite valeria por servidor
@Component
public class LimiteDeTentativas {

    static final Duration JANELA = Duration.ofMinutes(15);

    //Acima disto a memória passa a ser limpa dos endereços que já saíram da janela.
    //Sem isso, um ataque vindo de muitos endereços encheria a memória do servidor
    private static final int LIMPAR_ACIMA_DE = 10_000;

    //Quando a janela de um endereço começou e quantas tentativas ele já fez nela
    private record Conta(Instant inicio, int vezes) {
    }

    private final int maximo;
    private final Clock relogio;
    private final Map<String, Conta> contas = new ConcurrentHashMap<>();

    //O máximo vem da configuração para os testes poderem trocar; no ar fica o padrão, 10
    public LimiteDeTentativas(@Value("${atalaia.tentativas.maximo:10}") int maximo, Clock relogio) {
        this.maximo = maximo;
        this.relogio = relogio;
    }

    public boolean travado(String endereco) {
        Conta conta = contas.get(endereco);
        return conta != null && aberta(conta) && conta.vezes() >= maximo;
    }

    public void contar(String endereco) {
        if (contas.size() > LIMPAR_ACIMA_DE) {
            contas.values().removeIf(conta -> !aberta(conta));
        }
        //compute: ler e gravar de uma vez só, para duas tentativas ao mesmo tempo não contarem como uma
        contas.compute(endereco, (chave, conta) -> conta == null || !aberta(conta)
                ? new Conta(relogio.instant(), 1)
                : new Conta(conta.inicio(), conta.vezes() + 1));
    }

    //A janela ainda não acabou
    private boolean aberta(Conta conta) {
        return relogio.instant().isBefore(conta.inicio().plus(JANELA));
    }
}
