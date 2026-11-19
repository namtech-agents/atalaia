package br.com.namtech.atalaia.faxina;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.notificacao.CasoParado;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Faxina de sexta (US32): uma vez por semana, o escritório revê os casos abertos em que ninguém mexe há mais de 7 dias.
//Fecha o que só estava sem baixa e separa o que é urgente de verdade
@Controller
public class FaxinaController {

    //Parado é o caso sem nenhum evento no histórico há mais que isto
    static final Duration PARADO_HA = Duration.ofDays(7);

    //O que cada botão grava no histórico. Gravar um evento é "mexer" no caso: ele sai da faxina por mais 7 dias
    static final String SEGUE_ABERTO = "Na faxina: conferiu que o caso segue aberto";
    static final String ADIOU = "Na faxina: adiou a revisão para a próxima semana";

    private final NotificacaoRepository notificacoes;
    private final Historico historico;
    private final Clock relogio;

    public FaxinaController(NotificacaoRepository notificacoes, Historico historico, Clock relogio) {
        this.notificacoes = notificacoes;
        this.historico = historico;
        this.relogio = relogio;
    }

    @GetMapping("/faxina")
    public String faxina(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        Instant agora = Instant.now(relogio);
        //O vencido aparece primeiro: a data limite mais antiga vem em cima, e o vencido sempre tem data mais antiga
        //que o que ainda corre. Sem prazo fica embaixo, o parado há mais tempo primeiro
        List<CasoParado> parados = notificacoes.parados(usuario.escritorioId(), agora.minus(PARADO_HA)).stream()
                .sorted(Comparator.comparing(CasoParado::dataLimite, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CasoParado::ultimoMovimento))
                .toList();
        model.addAttribute("parados", parados);
        model.addAttribute("agora", agora);
        model.addAttribute("hoje", LocalDate.now(relogio));
        return "faxina";
    }

    //"Segue aberto" ou "adiar": grava no histórico. Resolver não passa por aqui: fechar pede desfecho e observação,
    //e a tela leva para o cartão "Fechar o caso" da notificação
    @PostMapping("/faxina/{id}")
    @Transactional
    public String anotar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam(defaultValue = "") String acao, RedirectAttributes recado) {
        String texto = switch (acao) {
            case "segue" -> SEGUE_ABERTO;
            case "adiar" -> ADIOU;
            //Só chega outro valor se alguém mexeu no formulário
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        //Tranca a linha: dois cliques (ou duas pessoas) gravam em fila, e o caso fechado no meio tempo não ganha anotação
        Notificacao caso = notificacoes.buscarParaAlterar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!caso.isResolvida()) {
            historico.registrar(caso, usuario.email(), texto);
            recado.addFlashAttribute("anotado", "Anotado no histórico do caso de " + caso.getEmpresa().getNome() + ".");
        }
        //Redireciona: recarregar a tela não grava de novo
        return "redirect:/faxina";
    }
}
