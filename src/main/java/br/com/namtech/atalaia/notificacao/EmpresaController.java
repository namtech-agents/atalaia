package br.com.namtech.atalaia.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import jakarta.servlet.http.HttpServletResponse;

//Empresas: os clientes do escritório. Cada escritório cadastra, corrige e exclui as suas, e só ve as suas
@Controller
public class EmpresaController {

    private static final String CNPJ_REPETIDO = "Este CNPJ já está cadastrado no escritório.";

    private final EmpresaRepository empresas;
    private final EscritorioRepository escritorios;
    //Os periodos da contagem por cliente, em dias: o mes, o trimestre e o ano. Outro valor no endereço vira o primeiro
    static final List<Integer> PERIODOS = List.of(30, 90, 365);

    private final NotificacaoRepository notificacoes;
    private final Clock relogio;

    public EmpresaController(EmpresaRepository empresas, EscritorioRepository escritorios,
            NotificacaoRepository notificacoes, Clock relogio) {
        this.empresas = empresas;
        this.escritorios = escritorios;
        this.notificacoes = notificacoes;
        this.relogio = relogio;
    }

    //O que a pessoa digitou, ja limpo: nome sem espaço nas pontas, CNPJ só com as 14 letras e numeros, em maiusculas
    private record Digitado(String nome, String cnpj) {
        static Digitado arrumar(String nome, String cnpj) {
            return new Digitado(nome.strip(), cnpj.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT));
        }
    }

    //Por que o formulario foi recusado: a mensagem e o campo que a tela marca e onde poe o foco
    private record Recusa(String campo, String mensagem) {
    }

    @GetMapping("/empresas")
    public String lista(@AuthenticationPrincipal UsuarioLogado usuario,
            @RequestParam(required = false) String cadastrada, @RequestParam(required = false) String alterada,
            @RequestParam(required = false) String excluida, @RequestParam(required = false) Integer periodo,
            Model model) {
        //?cadastrada, ?alterada ou ?excluida no endereço: a tela confirma o que acabou de acontecer
        model.addAttribute("cadastrada", cadastrada != null);
        model.addAttribute("alterada", alterada != null);
        model.addAttribute("excluida", excluida != null);
        model.addAttribute("empresas", empresas.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        //Quantos casos cada cliente teve no periodo escolhido, para o escritório rever a cobrança de quem da mais trabalho.
        //A conta sai dos casos do proprio Atalaia: conta a data em que o PDF foi enviado
        //periodo nulo testado antes: List.of(...).contains(null) lança erro em vez de responder "não"
        int dias = periodo != null && PERIODOS.contains(periodo) ? periodo : PERIODOS.get(0);
        Instant desde = Instant.now(relogio).minus(dias, ChronoUnit.DAYS);
        Map<Long, Long> casos = notificacoes.contarPorEmpresa(usuario.escritorioId(), desde).stream()
                .collect(Collectors.toMap(CasosDaEmpresa::empresaId, CasosDaEmpresa::casos));
        model.addAttribute("casosNoPeriodo", casos);
        model.addAttribute("periodo", dias);
        model.addAttribute("periodos", PERIODOS);
        return "empresas";
    }

    @PostMapping("/empresas")
    public String cadastrar(@AuthenticationPrincipal UsuarioLogado usuario, @RequestParam(defaultValue = "") String nome,
            @RequestParam(defaultValue = "") String cnpj, Model model, HttpServletResponse resposta) {
        Digitado digitado = Digitado.arrumar(nome, cnpj);
        Recusa recusa = conferir(digitado, usuario, null);
        if (recusa == null) {
            //getReferenceById: só aponta para o escritório pelo numero, sem ir busca-lo no banco
            recusa = gravar(new Empresa(escritorios.getReferenceById(usuario.escritorioId()), digitado.cnpj(),
                    digitado.nome()));
        }
        if (recusa != null) {
            mostrarRecusa(recusa, nome, cnpj, model, resposta);
            return lista(usuario, null, null, null, null, model);
        }
        //Redireciona: recarregar a tela nao cadastra de novo
        return "redirect:/empresas?cadastrada";
    }

    //A tela de uma empresa: corrigir o nome e o CNPJ e, se ela nao tiver casos, excluir
    @GetMapping("/empresas/{id}")
    public String empresa(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id, Model model) {
        Empresa empresa = buscar(id, usuario);
        model.addAttribute("empresa", empresa);
        model.addAttribute("casos", notificacoes.casosDaEmpresa(id, usuario.escritorioId()));
        //Na primeira vez os campos vem com o que esta guardado; numa recusa, com o que a pessoa digitou
        if (!model.containsAttribute("nome")) {
            model.addAttribute("nome", empresa.getNome());
            model.addAttribute("cnpj", empresa.getCnpjFormatado());
        }
        return "empresa";
    }

    @PostMapping("/empresas/{id}")
    public String corrigir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam(defaultValue = "") String nome, @RequestParam(defaultValue = "") String cnpj, Model model,
            HttpServletResponse resposta) {
        Empresa empresa = buscar(id, usuario);
        Digitado digitado = Digitado.arrumar(nome, cnpj);
        Recusa recusa = conferir(digitado, usuario, empresa.getCnpj());
        if (recusa == null) {
            //O save nao leva o escritório: a cerca aqui é a busca acima, com o escritório de quem entrou.
            //Do formulario só vem nome e CNPJ; o escritório da empresa nunca muda
            empresa.corrigir(digitado.cnpj(), digitado.nome());
            recusa = gravar(empresa);
        }
        if (recusa != null) {
            mostrarRecusa(recusa, nome, cnpj, model, resposta);
            return empresa(usuario, id, model);
        }
        return "redirect:/empresas?alterada";
    }

    @PostMapping("/empresas/{id}/excluir")
    public String excluir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id, Model model,
            HttpServletResponse resposta) {
        int apagadas;
        try {
            apagadas = empresas.excluirSemCasos(id, usuario.escritorioId());
        } catch (DataIntegrityViolationException casoChegouJunto) {
            //Um envio para esta empresa foi gravado no mesmo instante: o banco recusou apagar
            apagadas = 0;
        }
        if (apagadas == 0) {
            //Nao apagou: ou a empresa nao é deste escritório (ou ja saiu, clique duplo), e o empresa() abaixo
            //responde 404; ou ela tem casos. A tela nem mostra o botão para empresa com casos, mas a pagina pode
            //ter ficado aberta enquanto um colega enviava o primeiro caso: 409 (conflita com os casos) e a mesma
            //tela, que agora explica por que ela nao sai
            String tela = empresa(usuario, id, model);
            resposta.setStatus(HttpStatus.CONFLICT.value());
            return tela;
        }
        return "redirect:/empresas?excluida";
    }

    //Empresa de outro escritório responde igual a uma que nao existe (404): nem a existencia dela vaza
    private Empresa buscar(Long id, UsuarioLogado usuario) {
        return empresas.findByIdAndEscritorioId(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    //As regras do cadastro e da correção. cnpjAtual: o CNPJ que a empresa ja tem (null no cadastro), que nao
    //conta como repetido. Devolve null se esta tudo certo
    private Recusa conferir(Digitado digitado, UsuarioLogado usuario, String cnpjAtual) {
        if (digitado.nome().isEmpty() || digitado.nome().length() > 200) {
            return new Recusa("nome", "Escreva o nome da empresa, em até 200 letras.");
        }
        //12 letras ou numeros e 2 numeros no fim (os digitos de conferencia): o formato do CNPJ, inclusive o novo, com letras
        if (!digitado.cnpj().matches("[A-Z0-9]{12}[0-9]{2}")) {
            return new Recusa("cnpj", "Escreva o CNPJ completo, com os 14 dígitos.");
        }
        if (!Empresa.cnpjValido(digitado.cnpj())) {
            return new Recusa("cnpj", "Este CNPJ não existe: confira os números digitados.");
        }
        if (!digitado.cnpj().equals(cnpjAtual) && empresas.existsByEscritorioIdAndCnpj(usuario.escritorioId(), digitado.cnpj())) {
            return new Recusa("cnpj", CNPJ_REPETIDO);
        }
        return null;
    }

    //Grava, e devolve a recusa se o banco barrar o CNPJ repetido (empresa_cnpj_unico_no_escritorio).
    //Acontece no clique duplo, ou com duas pessoas usando o mesmo CNPJ ao mesmo tempo: os dois pedidos passaram
    //pela conferencia e o banco recusou o segundo. A pessoa ve a mensagem, nao uma pagina de erro
    private Recusa gravar(Empresa empresa) {
        try {
            empresas.save(empresa);
            return null;
        } catch (DataIntegrityViolationException repetida) {
            return new Recusa("cnpj", CNPJ_REPETIDO);
        } catch (ObjectOptimisticLockingFailureException excluidaNoMeio) {
            //Correção e exclusão no mesmo instante: a empresa saiu entre a busca e o save. Responde como sumida
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    //Volta o formulario com a mensagem e com o que a pessoa digitou, e responde 400 (pedido recusado)
    private void mostrarRecusa(Recusa recusa, String nome, String cnpj, Model model, HttpServletResponse resposta) {
        resposta.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("erro", recusa.mensagem());
        model.addAttribute("campoComErro", recusa.campo());
        model.addAttribute("nome", nome);
        model.addAttribute("cnpj", cnpj);
    }
}
