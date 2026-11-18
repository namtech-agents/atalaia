package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

//Notificação: um PDF enviado, com o que o Leitor tirou dele e o que o Analista entendeu. Cada objeto é uma linha da tabela notificacao
@Entity
public class Notificacao {

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
    private List<DadoExtraido> dados;

    private boolean precisaDeRevisao;

    //Um motivo por linha. Vazio quando nao precisa de revisao
    private String motivoDaRevisao;

    //Instant: um ponto exato no tempo, sem depender do fuso de quem le
    private Instant recebidaEm;

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
        this.motivoDaRevisao = precisaDeRevisao ? String.join("\n", motivosDaRevisao) : null;
        this.recebidaEm = Instant.now();
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
