package br.com.namtech.atalaia.conta;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//Conta: as telas de entrar e de criar a conta. Quem confere e-mail e senha na entrada é o Spring Security
//(ConfiguracaoDeSeguranca); aqui fica só mostrar as telas e criar a conta nova
@Controller
public class ContaController {

    //Senha curta é a que mais se adivinha. Comprimento vale mais que exigir simbolo e maiuscula
    static final int SENHA_MINIMA = 8;

    //O bcrypt só usa os primeiros 72 bytes da senha: acima disso o resto seria ignorado em silencio
    static final int SENHA_MAXIMA_EM_BYTES = 72;

    private final Contas contas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder embaralhador;

    public ContaController(Contas contas, UsuarioRepository usuarios, PasswordEncoder embaralhador) {
        this.contas = contas;
        this.usuarios = usuarios;
        this.embaralhador = embaralhador;
    }

    //Os avisos da tela chegam pelo endereço: /entrar?erro (o Spring Security manda para ca quando a senha
    //nao confere), ?saiu e ?travado (a TravaDeTentativas). Aqui cada um vira um sim ou nao para a tela
    @GetMapping("/entrar")
    public String entrar(@RequestParam(required = false) String erro, @RequestParam(required = false) String saiu,
            @RequestParam(required = false) String travado, Model model) {
        model.addAttribute("erro", erro != null);
        model.addAttribute("saiu", saiu != null);
        model.addAttribute("travado", travado != null);
        return "entrar";
    }

    @GetMapping("/cadastro")
    public String formulario(@RequestParam(required = false) String travado, Model model) {
        model.addAttribute("travado", travado != null);
        return "cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(@RequestParam(defaultValue = "") String escritorio,
            @RequestParam(defaultValue = "") String email, @RequestParam(defaultValue = "") String senha,
            @RequestParam(defaultValue = "") String senhaRepetida, Model model, HttpServletRequest pedido,
            HttpServletResponse resposta) throws ServletException {
        String nome = escritorio.strip();
        String emailArrumado = Contas.arrumar(email);

        Recusa recusa = motivoDaRecusa(nome, emailArrumado, senha, senhaRepetida);
        if (recusa == null) {
            try {
                contas.criar(nome, emailArrumado, embaralhador.encode(senha));
            } catch (DataIntegrityViolationException erro) {
                //Dois cadastros com o mesmo e-mail ao mesmo tempo (clique duplo): os dois passaram pela conferencia,
                //e o banco recusou o segundo. A pessoa ve a mesma mensagem de e-mail repetido, nao uma pagina de erro
                recusa = EMAIL_REPETIDO;
            }
        }
        if (recusa != null) {
            resposta.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erro", recusa.mensagem());
            //A tela marca e poe o foco no campo que falhou
            model.addAttribute("campoComErro", recusa.campo());
            //Devolve o que a pessoa ja digitou, menos a senha
            model.addAttribute("escritorio", escritorio);
            model.addAttribute("email", email);
            return "cadastro";
        }

        //A conta nasce com a sessão aberta: pedir o e-mail e a senha de novo, um segundo depois, nao protegeria nada.
        //login(): o proprio Spring Security confere a senha e abre a sessão, como faria na tela de entrar
        pedido.login(emailArrumado, senha);
        //Troca o numero da sessão na hora de entrar, como a tela de entrar ja faz sozinha.
        //Se alguem tivesse plantado um numero de sessão no navegador da pessoa antes, ele deixa de valer aqui
        pedido.changeSessionId();
        return "redirect:/";
    }

    //O que a tela precisa saber de um cadastro recusado: qual campo falhou e a mensagem para a pessoa
    private record Recusa(String campo, String mensagem) {
    }

    private static final Recusa EMAIL_REPETIDO = new Recusa("email", "Já existe uma conta com este e-mail. Entre com ela.");

    //Devolve a recusa, ou null se a conta pode ser criada
    private Recusa motivoDaRecusa(String nome, String email, String senha, String senhaRepetida) {
        //Nome sem nenhuma letra ou numero (só espaços, inclusive os invisiveis) nao é nome
        if (nome.codePoints().noneMatch(Character::isLetterOrDigit) || nome.length() > 200) {
            return new Recusa("escritorio", "Escreva o nome do escritório, em até 200 letras.");
        }
        //Conferencia simples: algo, arroba, algo, ponto, algo, sem espaço de nenhum tipo (\\p{Z} pega os invisiveis).
        //Se o e-mail existe mesmo, só enviando para ele
        if (email.length() > 254 || !email.matches("[^@\\s\\p{Z}]+@[^@\\s\\p{Z}]+\\.[^@\\s\\p{Z}]+")) {
            return new Recusa("email", "Escreva um e-mail válido, como nome@escritorio.com.br.");
        }
        if (senha.length() < SENHA_MINIMA || senha.isBlank()) {
            return new Recusa("senha", "A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_EM_BYTES) {
            return new Recusa("senha", "A senha é longa demais. Escolha uma mais curta.");
        }
        //A senha é digitada sem aparecer: repetir pega o erro de digitação antes de a pessoa ficar trancada para fora
        if (!senha.equals(senhaRepetida)) {
            return new Recusa("senha", "As duas senhas não são iguais. Digite de novo.");
        }
        if (usuarios.existsByEmail(email)) {
            return EMAIL_REPETIDO;
        }
        return null;
    }
}
