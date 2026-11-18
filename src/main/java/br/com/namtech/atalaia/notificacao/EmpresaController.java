package br.com.namtech.atalaia.notificacao;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import jakarta.servlet.http.HttpServletResponse;

//Empresas: os clientes do escritório. Cada escritório cadastra as suas, e só ve as suas
@Controller
public class EmpresaController {

    private final EmpresaRepository empresas;
    private final EscritorioRepository escritorios;

    public EmpresaController(EmpresaRepository empresas, EscritorioRepository escritorios) {
        this.empresas = empresas;
        this.escritorios = escritorios;
    }

    @GetMapping("/empresas")
    public String lista(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        model.addAttribute("empresas", empresas.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        return "empresas";
    }

    @PostMapping("/empresas")
    public String cadastrar(@AuthenticationPrincipal UsuarioLogado usuario, @RequestParam(defaultValue = "") String nome,
            @RequestParam(defaultValue = "") String cnpj, Model model, HttpServletResponse resposta) {
        String nomeArrumado = nome.strip();
        //A pessoa pode digitar com ou sem pontuação: guarda só as 14 letras e numeros, em maiusculas
        String cnpjArrumado = cnpj.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);

        String erro = null;
        if (nomeArrumado.isEmpty() || nomeArrumado.length() > 200) {
            erro = "Escreva o nome da empresa, em até 200 letras.";
        //12 letras ou numeros e 2 numeros no fim (os digitos de conferencia): o formato do CNPJ, inclusive o novo, com letras
        } else if (!cnpjArrumado.matches("[A-Z0-9]{12}[0-9]{2}")) {
            erro = "Escreva o CNPJ completo, com as 14 posições.";
        } else if (empresas.existsByEscritorioIdAndCnpj(usuario.escritorioId(), cnpjArrumado)) {
            erro = "Este CNPJ já está cadastrado no escritório.";
        }
        if (erro != null) {
            resposta.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erro", erro);
            model.addAttribute("nome", nome);
            model.addAttribute("cnpj", cnpj);
            return lista(usuario, model);
        }

        //getReferenceById: só aponta para o escritório pelo numero, sem ir busca-lo no banco
        empresas.save(new Empresa(escritorios.getReferenceById(usuario.escritorioId()), cnpjArrumado, nomeArrumado));
        //Redireciona: recarregar a tela nao cadastra de novo
        return "redirect:/empresas";
    }
}
