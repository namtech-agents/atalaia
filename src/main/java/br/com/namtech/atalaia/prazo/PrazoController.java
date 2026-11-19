package br.com.namtech.atalaia.prazo;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O prazo de uma notificação. A pessoa informa o dia da ciência e escolhe uma regra do escritório;
//a Calculadora faz a conta e a notificação guarda a data limite com a conta inteira. A IA nao participa
@Controller
public class PrazoController {

    //Ciência muito longe de hoje é erro de digitação (2206 no lugar de 2026)
    static final int ANOS_DE_FOLGA = 2;

    private final NotificacaoRepository notificacoes;
    private final RegraCadastradaRepository regras;
    private final Clock relogio;

    public PrazoController(NotificacaoRepository notificacoes, RegraCadastradaRepository regras) {
        this.notificacoes = notificacoes;
        this.regras = regras;
        this.relogio = Clock.system(Notificacao.TERESINA);
    }

    //Transactional: a tranca do buscarParaAlterar dura ate a conta ser gravada, e a gravação é feita no fim do metodo.
    //RedirectAttributes: o recado de erro viaja guardado na sessão ate a proxima tela (a da notificação) e some depois
    @PostMapping("/notificacoes/{id}/prazo")
    @Transactional
    public String calcular(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam(required = false) Long regraId, @RequestParam(defaultValue = "") String ciencia,
            RedirectAttributes recado) {
        //Notificação de outro escritório responde igual a uma que nao existe (404)
        Notificacao notificacao = notificacoes.buscarParaAlterar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        //O # no fim: a tela volta aberta no cartão do prazo
        String volta = "redirect:/notificacoes/" + id + "#titulo-prazo";
        //Caso fechado nao muda: a conta que estava la é a que valeu
        if (notificacao.isResolvida()) {
            return volta;
        }

        LocalDate hoje = LocalDate.now(relogio);
        LocalDate diaDaCiencia = data(ciencia);
        //Regra de outro escritório cai aqui tambem: para quem pede, ela nao existe
        RegraCadastrada regra = regraId == null ? null
                : regras.findByIdAndEscritorioId(regraId, usuario.escritorioId()).orElse(null);
        String erro = null;
        if (diaDaCiencia == null || diaDaCiencia.isBefore(hoje.minusYears(ANOS_DE_FOLGA))
                || diaDaCiencia.isAfter(hoje.plusYears(ANOS_DE_FOLGA))) {
            erro = "Informe o dia da ciência. Confira o ano.";
        } else if (regra == null) {
            erro = "Escolha a regra de prazo.";
        }
        if (erro != null) {
            recado.addFlashAttribute("erroDoPrazo", erro);
            recado.addFlashAttribute("cienciaDigitada", ciencia);
            recado.addFlashAttribute("regraEscolhida", regraId);
            return volta;
        }

        //O prazo maximo de uma regra é 365 dias: os feriados do ano da ciência e dos dois seguintes cobrem qualquer conta
        int ano = diaDaCiencia.getYear();
        CalculadoraDePrazo calculadora = new CalculadoraDePrazo(FeriadosNacionais.entre(ano, ano + 2), relogio);
        PrazoCalculado prazo = calculadora.calcular(regra.paraAConta(), diaDaCiencia);

        List<String> passos = new ArrayList<>(prazo.passos());
        passos.add(FeriadosNacionais.AVISO);
        //Se a Calculadora mandou para revisão, a data vem vazia: a tela mostra a conta e o motivo, sem data nenhuma
        notificacao.registrarPrazo(diaDaCiencia, prazo.dataLimite(), passos, usuario.email());
        return volta;
    }

    //O campo de data do navegador manda 2026-11-18. Devolve null se nao for uma data
    static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException erro) {
            return null;
        }
    }
}
