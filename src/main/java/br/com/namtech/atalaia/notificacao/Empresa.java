package br.com.namtech.atalaia.notificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

//Empresa: o cliente do escritório, dono da notificação. Cada objeto desta classe é uma linha da tabela empresa
@Entity
public class Empresa {

    //O banco é que gera o numero (identity)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //14 posições em texto: o CNPJ novo (2026) pode ter letras
    @Column(length = 14)
    private String cnpj;

    @Column(length = 200)
    private String nome;

    //O JPA exige um construtor vazio para montar o objeto quando le do banco
    protected Empresa() {
    }

    public Empresa(String cnpj, String nome) {
        this.cnpj = cnpj;
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    //O CNPJ como se escreve: 99.999.001/0001-00. Cada parentese pega um pedaço e o $ poe de volta com a pontuação
    public String getCnpjFormatado() {
        return cnpj.replaceFirst("(.{2})(.{3})(.{3})(.{4})(.{2})", "$1.$2.$3/$4-$5");
    }

    public String getNome() {
        return nome;
    }
}
