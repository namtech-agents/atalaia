package br.com.namtech.atalaia.conta;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//Na hora de entrar, o Spring Security pergunta aqui: "quem é a pessoa deste e-mail?".
//Ele mesmo compara a senha digitada com a embaralhada; aqui só se busca a conta. É aqui tambem que a conta nasce (criar)
@Service
public class Contas implements UserDetailsService {

    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;

    public Contas(UsuarioRepository usuarios, EscritorioRepository escritorios) {
        this.usuarios = usuarios;
        this.escritorios = escritorios;
    }

    //Transactional: a conversa com o banco fica aberta para ler o escritório do usuário (LAZY)
    @Override
    @Transactional(readOnly = true)
    public UsuarioLogado loadUserByUsername(String email) {
        Usuario usuario = usuarios.findByEmail(arrumar(email))
                .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada"));
        return new UsuarioLogado(usuario.getEmail(), usuario.getSenha(), usuario.getEscritorio().getId());
    }

    //Cria o escritório e a primeira pessoa dele. Transactional: ou grava os dois, ou nenhum.
    //Se o e-mail ja existir, o banco recusa (usuario_email_unico) e o erro sai daqui para quem chamou tratar
    @Transactional
    public void criar(String nomeDoEscritorio, String email, String senhaEmbaralhada) {
        Escritorio novo = escritorios.save(new Escritorio(nomeDoEscritorio));
        usuarios.save(new Usuario(novo, email, senhaEmbaralhada));
    }

    //O e-mail como é guardado: sem espaço nas pontas e em minusculas
    static String arrumar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
