package br.com.namtech.atalaia.conta;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

//Usuário: quem entra no Atalaia. Guarda só o necessario para entrar: e-mail, senha embaralhada e o escritório
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Escritorio escritorio;

    //Sempre em minusculas, para "Ana@x.com" e "ana@x.com" serem a mesma conta
    private String email;

    //Nunca a senha que a pessoa digitou: só o embaralhado dela (bcrypt), que nao da para desfazer
    private String senha;

    protected Usuario() {
    }

    public Usuario(Escritorio escritorio, String email, String senhaEmbaralhada) {
        this.escritorio = escritorio;
        this.email = email;
        this.senha = senhaEmbaralhada;
    }

    public Long getId() {
        return id;
    }

    public Escritorio getEscritorio() {
        return escritorio;
    }

    public String getEmail() {
        return email;
    }

    public String getSenha() {
        return senha;
    }
}
