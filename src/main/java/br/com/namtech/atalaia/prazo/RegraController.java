package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import jakarta.servlet.http.HttpServletResponse;

//Regras de prazo: o escritório cadastra as regras que ele mesmo conferiu na norma. Nenhuma regra vem pronta no
//codigo (RNF09): quem responde pela regra é quem leu o artigo, e o artigo e a data aparecem ao lado de todo prazo
@Controller
public class RegraController {

    //O "hoje" do Atalaia é o de Teresina, mesmo com o servidor em outro fuso
    public static final ZoneId TERESINA = ZoneId.of("America/Fortaleza");

    private static final String NOME_REPETIDO = "Já existe uma regra com este nome no escritório.";

    private final RegraCadastradaRepository regras;
    private final EscritorioRepository escritorios;

    public RegraController(RegraCadastradaRepository regras, EscritorioRepository escritorios) {
        this.regras = regras;
        this.escritorios = escritorios;
    }

    //Os avisos chegam pelo endereço (?cadastrada, ?excluida), como na tela Empresas
    @GetMapping("/regras")
    public String lista(@AuthenticationPrincipal UsuarioLogado usuario,
            @RequestParam(required = false) String cadastrada, @RequestParam(required = false) String excluida,
            Model model) {
        model.addAttribute("cadastrada", cadastrada != null);
        model.addAttribute("excluida", excluida != null);
        model.addAttribute("regras", regras.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        model.addAttribute("hoje", LocalDate.now(TERESINA));
        return "regras";
    }

    //Tudo chega como texto e é conferido aqui: assim cada erro vira uma frase para a pessoa,
    //e nao a pagina de erro que o Spring mostraria se "dias" viesse com letra
    @PostMapping("/regras")
    public String cadastrar(@AuthenticationPrincipal UsuarioLogado usuario,
            @RequestParam(defaultValue = "") String nome, @RequestParam(defaultValue = "") String dias,
            @RequestParam(defaultValue = "") String contagem,
            @RequestParam(defaultValue = "false") boolean suspendeNoRecesso,
            @RequestParam(defaultValue = "") String artigo, @RequestParam(defaultValue = "") String conferidaEm,
            Model model, HttpServletResponse resposta) {
        String nomeArrumado = nome.strip();
        String artigoArrumado = artigo.strip();
        LocalDate hoje = LocalDate.now(TERESINA);

        String campoComErro = null;
        String erro = null;
        RegraDePrazo regra = null;
        if (semLetraNemNumero(nomeArrumado) || nomeArrumado.length() > 120) {
            campoComErro = "nome";
            erro = "Escreva o nome da regra, em até 120 letras.";
        } else if (!dias.strip().matches("[0-9]{1,3}") || Integer.parseInt(dias.strip()) < 1
                || Integer.parseInt(dias.strip()) > 365) {
            campoComErro = "dias";
            erro = "Escreva o prazo em dias, de 1 a 365.";
        } else if (!contagem.equals("DIAS_CORRIDOS") && !contagem.equals("DIAS_UTEIS")) {
            campoComErro = "contagem";
            erro = "Escolha como os dias são contados.";
        } else if (semLetraNemNumero(artigoArrumado) || artigoArrumado.length() > 200) {
            campoComErro = "artigo";
            erro = "Escreva o artigo e a norma de onde a regra saiu, em até 200 letras.";
        } else if (dataDaConferencia(conferidaEm) == null || dataDaConferencia(conferidaEm).isAfter(hoje)) {
            campoComErro = "conferidaEm";
            erro = "Informe o dia em que a regra foi conferida na norma. Não pode ser uma data no futuro.";
        } else if (regras.existsByEscritorioIdAndNome(usuario.escritorioId(), nomeArrumado)) {
            campoComErro = "nome";
            erro = NOME_REPETIDO;
        } else {
            regra = new RegraDePrazo(nomeArrumado, Integer.parseInt(dias.strip()), TipoDeContagem.valueOf(contagem),
                    suspendeNoRecesso, artigoArrumado, dataDaConferencia(conferidaEm));
            try {
                regras.save(new RegraCadastrada(escritorios.getReferenceById(usuario.escritorioId()), regra));
            } catch (DataIntegrityViolationException repetida) {
                //Clique duplo: os dois pedidos passaram pela conferencia de cima e o banco recusou o segundo
                campoComErro = "nome";
                erro = NOME_REPETIDO;
            }
        }
        if (erro != null) {
            resposta.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erro", erro);
            model.addAttribute("campoComErro", campoComErro);
            //Devolve o que a pessoa ja digitou
            model.addAttribute("nome", nome);
            model.addAttribute("dias", dias);
            model.addAttribute("contagem", contagem);
            model.addAttribute("suspendeNoRecesso", suspendeNoRecesso);
            model.addAttribute("artigo", artigo);
            model.addAttribute("conferidaEm", conferidaEm);
            return lista(usuario, null, null, model);
        }
        //Redireciona: recarregar a tela nao cadastra de novo
        return "redirect:/regras?cadastrada";
    }

    //Regra cadastrada errada daria prazo errado: por isso da para excluir. O prazo ja calculado com ela nao muda,
    //porque a notificação guarda a conta inteira, e nao um apontamento para a regra
    @PostMapping("/regras/{id}/excluir")
    @Transactional
    public String excluir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id) {
        //Regra de outro escritório responde igual a uma que nao existe (404)
        RegraCadastrada regra = regras.findByIdAndEscritorioId(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        regras.delete(regra);
        return "redirect:/regras?excluida";
    }

    //Só espaços, inclusive os invisiveis, nao é nome nem artigo
    private static boolean semLetraNemNumero(String texto) {
        return texto.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    //O campo de data do navegador manda 2026-11-18. Devolve null se nao for uma data
    private static LocalDate dataDaConferencia(String texto) {
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException erro) {
            return null;
        }
    }
}
