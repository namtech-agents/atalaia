package br.com.namtech.atalaia.conta;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

//Conta: as telas de entrar e de criar a conta. Quem confere e-mail e senha na entrada é o Spring Security
//(ConfiguracaoDeSeguranca); aqui fica só mostrar as telas e criar a conta nova
@Controller
public class ContaController {

    //Senha curta é a que mais se adivinha. Comprimento vale mais que exigir simbolo e maiuscula
    static final int SENHA_MINIMA = 8;

    //O bcrypt só usa os primeiros 72 bytes da senha: acima disso o resto seria ignorado em silencio
    static final int SENHA_MAXIMA_EM_BYTES = 72;

    private final EscritorioRepository escritorios;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder embaralhador;

    public ContaController(EscritorioRepository escritorios, UsuarioRepository usuarios, PasswordEncoder embaralhador) {
        this.escritorios = escritorios;
        this.usuarios = usuarios;
        this.embaralhador = embaralhador;
    }

    //Os avisos da tela chegam pelo endereço: /entrar?erro (o Spring Security manda para ca quando a senha
    //nao confere), ?criada (conta nova) e ?saiu. Aqui cada um vira um sim ou nao para a tela
    @GetMapping("/entrar")
    public String entrar(@RequestParam(required = false) String erro, @RequestParam(required = false) String criada,
            @RequestParam(required = false) String saiu, Model model) {
        model.addAttribute("erro", erro != null);
        model.addAttribute("criada", criada != null);
        model.addAttribute("saiu", saiu != null);
        return "entrar";
    }

    @GetMapping("/cadastro")
    public String formulario() {
        return "cadastro";
    }

    //Cria o escritório e a primeira pessoa dele. Transactional: ou grava os dois, ou nenhum
    @PostMapping("/cadastro")
    @Transactional
    public String cadastrar(@RequestParam(defaultValue = "") String escritorio,
            @RequestParam(defaultValue = "") String email, @RequestParam(defaultValue = "") String senha,
            Model model, HttpServletResponse resposta) {
        String nome = escritorio.strip();
        String emailArrumado = Contas.arrumar(email);

        String erro = motivoDaRecusa(nome, emailArrumado, senha);
        if (erro != null) {
            resposta.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erro", erro);
            //Devolve o que a pessoa ja digitou, menos a senha
            model.addAttribute("escritorio", escritorio);
            model.addAttribute("email", email);
            return "cadastro";
        }

        Escritorio novo = escritorios.save(new Escritorio(nome));
        usuarios.save(new Usuario(novo, emailArrumado, embaralhador.encode(senha)));
        //Redireciona para a tela de entrar, com o aviso de que a conta foi criada
        return "redirect:/entrar?criada";
    }

    //Devolve a mensagem para a pessoa, ou null se a conta pode ser criada
    private String motivoDaRecusa(String nome, String email, String senha) {
        if (nome.isEmpty() || nome.length() > 200) {
            return "Escreva o nome do escritório, em até 200 letras.";
        }
        //Conferencia simples: algo, arroba, algo, ponto, algo, sem espaço. Se o e-mail existe mesmo, só enviando para ele
        if (email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "Escreva um e-mail válido, como nome@escritorio.com.br.";
        }
        if (senha.length() < SENHA_MINIMA) {
            return "A senha precisa ter pelo menos " + SENHA_MINIMA + " letras ou números.";
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_EM_BYTES) {
            return "A senha é longa demais. Use até " + SENHA_MAXIMA_EM_BYTES + " letras ou números.";
        }
        if (usuarios.existsByEmail(email)) {
            return "Já existe uma conta com este e-mail. Entre com ela.";
        }
        return null;
    }
}
