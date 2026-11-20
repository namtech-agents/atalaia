package br.com.namtech.atalaia.historico;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import br.com.namtech.atalaia.notificacao.Notificacao;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

//Uma coisa que aconteceu com um caso: quando, quem e o que. Nao tem setter: depois de gravado, o evento nao muda
@Entity
public class EventoDoCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Notificacao notificacao;

    private Instant quando;

    //O e-mail de quem fez, ou "Cliente, pelo link". Vazio (null) nos casos de antes do historico existir (V11)
    private String quem;

    //A frase pronta, como a tela mostra
    private String acao;

    //SHA-256 dos dados deste evento junto com a assinatura do anterior (V14). Vazio nos eventos de antes da trilha
    private String assinatura;

    protected EventoDoCaso() {
    }

    //"anterior": a assinatura do evento anterior do caso, ou "" no primeiro da trilha
    EventoDoCaso(Notificacao notificacao, String quem, String acao, Instant quando, String anterior) {
        this.notificacao = notificacao;
        this.quem = quem;
        this.acao = acao;
        this.quando = quando;
        this.assinatura = assinar(anterior, notificacao.getId(), quando, quem, acao);
    }

    //A assinatura de um evento. Cada campo entra com o tamanho na frente ("12:Enviou o PDF"): assim nenhuma
    //combinação de textos diferentes vira a mesma sequencia (sem o tamanho, "a|b" + "c" e "a" + "b|c" empatariam).
    //O instante entra em milissegundos, a mesma precisão que o Historico grava e o banco devolve
    static String assinar(String anterior, Long notificacaoId, Instant quando, String quem, String acao) {
        StringBuilder dados = new StringBuilder();
        for (String campo : new String[] { anterior, String.valueOf(notificacaoId),
                String.valueOf(quando.toEpochMilli()), quem == null ? "" : quem, acao }) {
            dados.append(campo.length()).append(':').append(campo);
        }
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(dados.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException impossivel) {
            //Todo Java traz o SHA-256 (a especificação exige); se faltar, o servidor está quebrado
            throw new IllegalStateException(impossivel);
        }
    }

    //Confere a assinatura guardada com a que sai dos dados de agora. Falso se algum campo mudou depois de gravado
    boolean confere(String anterior) {
        return assinatura != null
                && assinatura.equals(assinar(anterior, notificacao.getId(), quando, quem, acao));
    }

    Long getId() {
        return id;
    }

    public String getAssinatura() {
        return assinatura;
    }

    public String getQuandoPorExtenso() {
        return Notificacao.DIA_E_HORA.format(quando);
    }

    public String getQuem() {
        return quem;
    }

    public String getAcao() {
        return acao;
    }
}
