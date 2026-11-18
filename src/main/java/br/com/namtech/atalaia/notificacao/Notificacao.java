package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

//Notificação: um PDF enviado, com o que o Leitor tirou dele. Cada objeto é uma linha da tabela notificacao
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

    private boolean precisaDeRevisao;

    //Instant: um ponto exato no tempo, sem depender do fuso de quem le
    private Instant recebidaEm;

    protected Notificacao() {
    }

    public Notificacao(Empresa empresa, byte[] arquivo, String texto, boolean precisaDeRevisao) {
        this.empresa = empresa;
        this.arquivo = arquivo;
        this.texto = texto;
        this.precisaDeRevisao = precisaDeRevisao;
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

    public boolean isPrecisaDeRevisao() {
        return precisaDeRevisao;
    }

    public Instant getRecebidaEm() {
        return recebidaEm;
    }
}
