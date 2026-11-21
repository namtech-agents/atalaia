package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import br.com.namtech.atalaia.analista.Degrau;
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

    //Só existe quando o original trazia scripts: a mesma notificação sem eles, que é a que a tela mostra
    private byte[] arquivoSemScripts;

    //Texto que o Leitor tirou do PDF (no banco a coluna é do tipo text, sem limite de tamanho)
    private String texto;

    //Fica vazia enquanto o prazo nao passa pelas conferencias
    private LocalDate dataLimite;

    //O tipo que o Analista deu. STRING: guarda o nome (INTIMACAO), nao a posição na lista. Vazio se nao classificou
    @Enumerated(EnumType.STRING)
    private TipoDeDocumento tipo;

    //A gravidade (US28): sugerida pela IA, corrigida pelo contador. Vazio se ninguém deu um ainda
    @Enumerated(EnumType.STRING)
    private Degrau degrau;

    //Quem corrigiu o degrau por último (e-mail). Vazio enquanto o degrau é o que a IA sugeriu
    private String degrauPor;

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

    //Simulado (US30): notificação de teste solta pelo escritório para medir o tempo da equipe
    private boolean simulado;

    //A primeira vez que alguém do escritório abriu o caso. Só é marcada no simulado (NotificacaoRepository.marcarAbertura)
    private Instant abertaEm;

    //Quem soltou o simulado: a abertura dessa pessoa não conta, porque ela já sabe que é teste
    private String soltoPor;

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
        guardarMotivos(motivosDaRevisao);
        this.recebidaEm = Instant.now();
    }

    //Os motivos da revisão numa coluna só, um por linha
    private void guardarMotivos(List<String> motivosDaRevisao) {
        this.precisaDeRevisao = !motivosDaRevisao.isEmpty();
        //Um motivo pode citar um nome de campo vindo da IA: sem quebra de linha dentro dele, para nao virar dois motivos
        this.motivoDaRevisao = precisaDeRevisao
                ? motivosDaRevisao.stream().map(motivo -> motivo.replaceAll("\\R", " ")).collect(Collectors.joining("\n"))
                : null;
    }

    //Uma leitura nova, pedida pela pessoa (#216): quando a IA falhou na chegada (fora do ar, resposta estranha), o
    //caso ficava sem tipo e sem dados para sempre. Troca o que a leitura anterior deixou: tipo, dados, revisão e as
    //sugestões de resumo e gravidade. A gravidade que uma pessoa já corrigiu fica: a decisão dela vale mais
    public void lerDeNovo(TipoDeDocumento tipo, List<DadoExtraido> dados, List<String> motivosDaRevisao,
            String resumo, Degrau degrau) {
        this.tipo = tipo;
        //Lista nova (e não clear/addAll): a do construtor pode ser uma lista que não aceita mudança
        this.dados = new ArrayList<>(dados);
        guardarMotivos(motivosDaRevisao);
        sugerirResumo(resumo);
        if (degrauPor == null) {
            sugerirDegrau(degrau);
        }
    }

    //O tipo escolhido por uma pessoa (#216), para o caso que a IA não conseguiu classificar. Quem chama grava no
    //histórico. Os dados continuam escondidos enquanto o caso pede revisão: o tipo não prova que a leitura está certa
    public void corrigirTipo(TipoDeDocumento tipo) {
        this.tipo = tipo;
    }

    //Um simulado: o documento de teste, já classificado, sem passar pela IA (não gasta chamada) e sem motivo de revisão,
    //para seguir o caminho de um caso comum: calcular o prazo, avisar o cliente, fechar
    public static Notificacao simulado(Empresa empresa, byte[] pdf, String texto, String quem) {
        Notificacao teste = new Notificacao(empresa, pdf, texto, TipoDeDocumento.INTIMACAO, List.of(), List.of());
        teste.simulado = true;
        teste.soltoPor = quem;
        //O documento ameaça auto de infração se não for atendido: é exigência sob pena
        teste.degrau = Degrau.EXIGENCIA_SOB_PENA;
        return teste;
    }

    public boolean isSimulado() {
        return simulado;
    }

    public Instant getAbertaEm() {
        return abertaEm;
    }

    //Guarda o rascunho da IA para o aviso ao cliente. Documento que foi para revisao nao guarda:
    //se nem os dados dele sao mostrados, um resumo feito em cima deles tambem nao é
    public void sugerirResumo(String resumo) {
        this.resumoSugerido = precisaDeRevisao ? null : resumo;
    }

    //O degrau sugerido pela IA. Documento que foi para revisão não guarda, pela mesma regra do resumo:
    //a leitura em que ninguém confia não decide a gravidade. A tela pede para a pessoa escolher
    public void sugerirDegrau(Degrau degrau) {
        this.degrau = precisaDeRevisao ? null : degrau;
        this.degrauPor = null;
    }

    //A correção de uma pessoa. Quem chama grava no histórico (DegrauController)
    public void corrigirDegrau(Degrau degrau, String quem) {
        this.degrau = degrau;
        this.degrauPor = quem;
    }

    public String getDegrauPor() {
        return degrauPor;
    }

    public Degrau getDegrau() {
        return degrau;
    }

    public String getResumoSugerido() {
        return resumoSugerido;
    }

    //Fecha o caso. Resolver de novo um caso fechado nao troca o registro: para corrigir, primeiro reabre
    public void resolver(Desfecho desfecho, String observacao) {
        if (isResolvida()) {
            throw new IllegalStateException("A notificação " + id + " já foi resolvida");
        }
        this.desfecho = desfecho;
        this.observacao = observacao;
        this.resolvidaEm = Instant.now();
    }

    //Volta o caso para aberto (desfecho marcado errado, ou o caso voltou a andar).
    //Os tres campos saem juntos: o banco só aceita os tres vazios ou os tres preenchidos (V4).
    //Quem chama guarda antes o que estava registrado no historico, que nao apaga nada
    public void reabrir() {
        if (!isResolvida()) {
            throw new IllegalStateException("A notificação " + id + " não está resolvida");
        }
        this.desfecho = null;
        this.observacao = null;
        this.resolvidaEm = null;
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

    public byte[] getArquivoSemScripts() {
        return arquivoSemScripts;
    }

    //Guarda a copia sem scripts que o Leitor fez (null quando nao havia script)
    public void guardarCopiaSemScripts(byte[] copia) {
        this.arquivoSemScripts = copia;
    }

    //O PDF que vai para a tela: a copia sem scripts, se o original tinha algum; senao o proprio original
    public byte[] getArquivoParaMostrar() {
        return arquivoSemScripts != null ? arquivoSemScripts : arquivo;
    }

    public boolean isTinhaScripts() {
        return arquivoSemScripts != null;
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
