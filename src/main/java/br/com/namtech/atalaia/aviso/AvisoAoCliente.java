package br.com.namtech.atalaia.aviso;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.namtech.atalaia.notificacao.Notificacao;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

//Um aviso mandado ao cliente: o que ele viu (resumo, prazo e documentos) e o registro de ciência.
//O que foi mostrado fica copiado aqui e nao muda mais: é isso que da valor ao registro.
//Uma notificação pode ter varios avisos; o mais novo é o que vale
@Entity
public class AvisoAoCliente {

    //O link vale 30 dias: tempo de sobra para o cliente abrir, sem deixar um endereço valendo para sempre
    static final Duration VALIDADE = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Notificacao notificacao;

    private String token;

    private String resumo;

    //Um documento por linha. Vazio (null) se o escritório nao pediu nenhum
    private String documentos;

    private LocalDate prazo;

    private Instant criadoEm;

    //O e-mail de quem fez o aviso
    private String criadoPor;

    private Instant valeAte;

    private Instant canceladoEm;

    private Instant confirmadoEm;

    //A data que o cliente marcou na pergunta de conferência
    private LocalDate resposta;

    protected AvisoAoCliente() {
    }

    //"agora" vem de fora (do relogio de quem chama) para o teste poder criar um aviso antigo
    public AvisoAoCliente(Notificacao notificacao, String token, String resumo, String documentos, String quem,
            Instant agora) {
        this.notificacao = notificacao;
        this.token = token;
        this.resumo = resumo;
        this.documentos = documentos;
        //Copia a data limite que a notificação tem neste momento: é a que o cliente vai ver
        this.prazo = notificacao.getDataLimite();
        this.criadoPor = quem;
        this.criadoEm = agora;
        this.valeAte = agora.plus(VALIDADE);
    }

    public SituacaoDoAviso situacao(Instant agora) {
        if (confirmadoEm != null) {
            return SituacaoDoAviso.CONFIRMADO;
        }
        if (canceladoEm != null) {
            return SituacaoDoAviso.SUBSTITUIDO;
        }
        return agora.isAfter(valeAte) ? SituacaoDoAviso.VENCIDO : SituacaoDoAviso.AGUARDANDO;
    }

    //O registro de ciência. Só o aviso que ainda aguarda pode ser confirmado, e uma vez só
    public void confirmar(LocalDate resposta, Instant agora) {
        if (situacao(agora) != SituacaoDoAviso.AGUARDANDO) {
            throw new IllegalStateException("O aviso " + id + " não está aguardando confirmação");
        }
        this.resposta = resposta;
        this.confirmadoEm = agora;
    }

    //O escritório fez um aviso novo: este link deixa de confirmar. Aviso ja confirmado fica como esta, é registro
    public void substituir(Instant agora) {
        if (confirmadoEm == null && canceladoEm == null) {
            this.canceladoEm = agora;
        }
    }

    //A pergunta de conferência: tres datas, uma delas a data limite. O codigo monta, a IA nao participa.
    //A posição da certa muda de aviso para aviso (sai do token), para "a do meio" nao virar a resposta de sempre
    public List<LocalDate> opcoesDePrazo() {
        int semanasAntes = Math.floorMod(token.hashCode(), 3);
        LocalDate primeira = prazo.minusWeeks(semanasAntes);
        return List.of(primeira, primeira.plusWeeks(1), primeira.plusWeeks(2));
    }

    public boolean acertouOPrazo() {
        return prazo.equals(resposta);
    }

    //O cliente viu uma data e a conta do prazo foi refeita depois: o aviso ficou velho
    public boolean prazoMudou(LocalDate dataLimiteDoCaso) {
        return !prazo.equals(dataLimiteDoCaso);
    }

    //O que o escritório precisa saber alem da situação, ou null se nao ha nada. A data que mudou vem primeiro:
    //ela pede um aviso novo, e ai a resposta antiga do cliente deixa de importar
    private String ressalva(LocalDate dataLimiteDoCaso) {
        if (prazoMudou(dataLimiteDoCaso)) {
            return "mas a data limite mudou";
        }
        return confirmadoEm != null && !acertouOPrazo() ? "com a data errada" : null;
    }

    //O texto da etiqueta na lista da tela inicial: a situação e, se houver, a ressalva por extenso
    //(quem nao distingue a cor nao perde a informação)
    public String naLista(Instant agora, LocalDate dataLimiteDoCaso) {
        String ressalva = ressalva(dataLimiteDoCaso);
        return situacao(agora).getNome() + (ressalva == null ? "" : ", " + ressalva);
    }

    //Na lista, a etiqueta fica em vermelho quando o escritório precisa agir: o link venceu sem resposta,
    //a data limite mudou depois do aviso, ou o cliente confirmou marcando a data errada (vale uma ligação)
    public boolean pedeAtencao(Instant agora, LocalDate dataLimiteDoCaso) {
        return situacao(agora) == SituacaoDoAviso.VENCIDO || ressalva(dataLimiteDoCaso) != null;
    }

    //De qual notificação é o aviso, para a lista juntar um com o outro. Pedir só o numero nao vai ao banco;
    //qualquer outro campo da notificação, pedido de dentro da lista, daria erro: a conversa com o banco ja fechou
    public Long getNotificacaoId() {
        return notificacao.getId();
    }

    public List<String> getListaDeDocumentos() {
        return documentos == null ? List.of() : documentos.lines().toList();
    }

    //As datas com hora, ja escritas no horario de Teresina, para as telas
    //O simulado conta o tempo até o primeiro aviso (TemposDoSimulado)
    public Instant getCriadoEm() {
        return criadoEm;
    }

    public String getCriadoEmPorExtenso() {
        return Notificacao.DIA_E_HORA.format(criadoEm);
    }

    public String getValeAtePorExtenso() {
        return Notificacao.DIA_E_HORA.format(valeAte);
    }

    public String getConfirmadoEmPorExtenso() {
        return confirmadoEm == null ? null : Notificacao.DIA_E_HORA.format(confirmadoEm);
    }

    public Notificacao getNotificacao() {
        return notificacao;
    }

    public String getToken() {
        return token;
    }

    public String getResumo() {
        return resumo;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public LocalDate getResposta() {
        return resposta;
    }
}
