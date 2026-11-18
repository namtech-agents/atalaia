package br.com.namtech.atalaia.conta;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

//Quem esta com a sessão aberta. O Spring Security guarda este objeto enquanto a pessoa navega,
//e os controllers pedem ele para saber de qual escritório sao os dados (escritorioId).
//UserDetails: o formato que o Spring Security entende (nome de entrada, senha e permissões)
public record UsuarioLogado(String email, String senha, Long escritorioId) implements UserDetails {

    //O toString pronto do record escreveria a senha embaralhada: se este objeto for parar num registro (log), ela nao vai junto
    @Override
    public String toString() {
        return "UsuarioLogado[email=" + email + ", escritorioId=" + escritorioId + "]";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senha;
    }

    //Sem papeis por enquanto: todo mundo que entra pode o mesmo, dentro do proprio escritório
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
