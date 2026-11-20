package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;

import br.com.namtech.atalaia.conta.Escritorio;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

//Uma regra de prazo guardada no banco (tabela regra_de_prazo). Quem cadastra é o escritório, depois de ler a regra
//na norma: o Atalaia nao traz regra pronta. A Calculadora nao conhece esta classe: recebe a RegraDePrazo (paraAConta)
@Entity
@Table(name = "regra_de_prazo")
public class RegraCadastrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //O escritório dono da regra. Toda busca de regra passa por ele
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Escritorio escritorio;

    private String nome;

    private int dias;

    //STRING: guarda o nome (DIAS_UTEIS), nao a posição na lista
    @Enumerated(EnumType.STRING)
    private TipoDeContagem contagem;

    private boolean suspendeNoRecesso;

    private String artigo;

    private LocalDate conferidaEm;

    protected RegraCadastrada() {
    }

    public RegraCadastrada(Escritorio escritorio, RegraDePrazo regra) {
        this.escritorio = escritorio;
        this.nome = regra.nome();
        this.dias = regra.dias();
        this.contagem = regra.contagem();
        this.suspendeNoRecesso = regra.suspendeNoRecesso();
        this.artigo = regra.artigo();
        this.conferidaEm = regra.conferidaEm();
    }

    //A regra no formato que a Calculadora recebe
    public RegraDePrazo paraAConta() {
        return new RegraDePrazo(nome, dias, contagem, suspendeNoRecesso, artigo, conferidaEm);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
