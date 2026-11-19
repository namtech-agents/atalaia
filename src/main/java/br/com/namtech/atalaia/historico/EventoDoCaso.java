package br.com.namtech.atalaia.historico;

import java.time.Instant;

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

    protected EventoDoCaso() {
    }

    EventoDoCaso(Notificacao notificacao, String quem, String acao, Instant quando) {
        this.notificacao = notificacao;
        this.quem = quem;
        this.acao = acao;
        this.quando = quando;
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
