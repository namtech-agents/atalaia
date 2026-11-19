package br.com.namtech.atalaia.conta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

//Escritório: o escritório de contabilidade que usa o Atalaia. É a "cerca" dos dados:
//cada empresa e cada usuário pertencem a um escritório, e ninguém ve o que é de outro
@Entity
public class Escritorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String nome;

    protected Escritorio() {
    }

    public Escritorio(String nome) {
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
