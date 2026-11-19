package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.verificador.DadoExtraido;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;

//Notificação: um PDF enviado, com o que o Leitor tirou dele e o que o Analista entendeu. Cada objeto é uma linha da tabela notificacao
@Entity
public class Notificacao {

    //O fuso do Atalaia: o "hoje" e as horas da tela sao os de Teresina, mesmo com o servidor em outro fuso
    public static final ZoneId TERESINA = ZoneId.of("America/Fortaleza");

    //Data e hora como se escreve aqui, no horario de Teresina. Serve a todas as telas
    public static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(TERESINA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //Muitas notificações para uma empresa. LAZY: so busca a empresa no banco quando alguem pedir
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Empresa empresa;

    //O PDF original, do jeito que chegou
    private byte[] arquivo;

    //Texto que o Leitor tirou do PDF (no banco a coluna é do tipo text, sem limite de tamanho)
    private String texto;

    //Fica vazia enquanto o prazo nao passa pelas conferencias
    private LocalDate dataLimite;

    //O tipo que o Analista deu. STRING: guarda o nome (INTIMACAO), nao a posição na lista. Vazio se nao classificou
    @Enumerated(EnumType.STRING)
    private TipoDeDocumento tipo;

    //Os dados que o Analista tirou do documento. Ficam numa tabela propria (dado_extraido), ligada por notificacao_id
    @ElementCollection
    @CollectionTable(name = "dado_extraido", joinColumns = @JoinColumn(name = "notificacao_id"))
    //OrderColumn: guarda a posição de cada dado, para a lista voltar do banco na mesma ordem
    @OrderColumn(name = "ordem")
    private List<DadoExtraido> dados;

    private boolean precisaDeRevisao;

    //Um motivo por linha. Vazio quando nao precisa de revisao
    private String motivoDaRevisao;

    //Instant: um ponto exato no tempo, sem depender do fuso de quem le
    private Instant recebidaEm;

    //Os tres ficam vazios enquanto o caso esta aberto, e sao preenchidos juntos em resolver()
    private Instant resolvidaEm;

    @Enumerated(EnumType.STRING)
    private Desfecho desfecho;

    private String observacao;

    //O rascunho do aviso ao cliente escrito pela IA. Vazio se ela nao sugeriu ou se o documento foi para revisao
    private String resumoSugerido;

    //A conta do prazo: os quatro ficam vazios ate alguem calcular, e sao preenchidos juntos em registrarPrazo()
    private LocalDate ciencia;

    //Um passo por linha. É uma copia: se a regra mudar ou for excluida depois, o que a pessoa viu continua igual
    private String contaDoPrazo;

    private Instant prazoCalculadoEm;

    //O e-mail de quem pediu a conta
    private String prazoCalculadoPor;

    protected Notificacao() {
    }

    //Quem cria nao diz se precisa de revisao: precisa sempre que houver pelo menos um motivo
    public Notificacao(Empresa empresa, byte[] arquivo, String texto, TipoDeDocumento tipo, List<DadoExtraido> dados,
            List<String> motivosDaRevisao) {
        this.empresa = empresa;
        this.arquivo = arquivo;
        this.texto = texto;
        this.tipo = tipo;
        this.dados = dados;
        this.precisaDeRevisao = !motivosDaRevisao.isEmpty();
        //Um motivo pode citar um nome de campo vindo da IA: sem quebra de linha dentro dele, para nao virar dois motivos
        this.motivoDaRevisao = precisaDeRevisao
                ? motivosDaRevisao.stream().map(motivo -> motivo.replaceAll("\\R", " ")).collect(Collectors.joining("\n"))
                : null;
        this.recebidaEm = Instant.now();
    }

    //Guarda o rascunho da IA para o aviso ao cliente. Documento que foi para revisao nao guarda:
    //se nem os dados dele sao mostrados, um resumo feito em cima deles tambem nao é
    public void sugerirResumo(String resumo) {
        this.resumoSugerido = precisaDeRevisao ? null : resumo;
    }

    public String getResumoSugerido() {
        return resumoSugerido;
    }

    //Fecha o caso. Uma notificação só se resolve uma vez: o que foi registrado nao é trocado depois
    public void resolver(Desfecho desfecho, String observacao) {
        if (isResolvida()) {
            throw new IllegalStateException("A notificação " + id + " já foi resolvida");
        }
        this.desfecho = desfecho;
        this.observacao = observacao;
        this.resolvidaEm = Instant.now();
    }

    //Guarda a conta do prazo. dataLimite vem vazia quando a Calculadora mandou o caso para revisão.
    //Da para refazer enquanto o caso esta aberto; depois de resolvido, a conta que valeu nao muda
    public void registrarPrazo(LocalDate ciencia, LocalDate dataLimite, List<String> passos, String quem) {
        if (isResolvida()) {
            throw new IllegalStateException("A notificação " + id + " já foi resolvida");
        }
        this.ciencia = ciencia;
        this.dataLimite = dataLimite;
        this.contaDoPrazo = String.join("\n", passos);
        this.prazoCalculadoEm = Instant.now();
        this.prazoCalculadoPor = quem;
    }

    public LocalDate getCiencia() {
        return ciencia;
    }

    //Os passos separados de novo, um por item. Vazio se ninguem calculou
    public List<String> getPassosDoPrazo() {
        return contaDoPrazo == null ? List.of() : contaDoPrazo.lines().toList();
    }

    public Instant getPrazoCalculadoEm() {
        return prazoCalculadoEm;
    }

    public String getPrazoCalculadoPor() {
        return prazoCalculadoPor;
    }

    public boolean isResolvida() {
        return resolvidaEm != null;
    }

    public Instant getResolvidaEm() {
        return resolvidaEm;
    }

    public Desfecho getDesfecho() {
        return desfecho;
    }

    public String getObservacao() {
        return observacao;
    }

    public Long getId() {
        return id;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public byte[] getArquivo() {
        return arquivo;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDate getDataLimite() {
        return dataLimite;
    }

    public TipoDeDocumento getTipo() {
        return tipo;
    }

    public List<DadoExtraido> getDados() {
        return dados;
    }

    //Devolve os motivos separados de novo, um por item
    public List<String> getMotivosDaRevisao() {
        return motivoDaRevisao == null ? List.of() : motivoDaRevisao.lines().toList();
    }

    public boolean isPrecisaDeRevisao() {
        return precisaDeRevisao;
    }

    public Instant getRecebidaEm() {
        return recebidaEm;
    }
}
