package br.com.namtech.atalaia.recepcao;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.analista.Degrau;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O contador corrige o degrau (US28) com um clique. A correção vai para o histórico, com o degrau de antes
@Controller
public class DegrauController {

    private final NotificacaoRepository notificacoes;
    private final Historico historico;

    public DegrauController(NotificacaoRepository notificacoes, Historico historico) {
        this.notificacoes = notificacoes;
        this.historico = historico;
    }

    //Degrau que não é um dos cinco (formulário mexido) o Spring recusa sozinho, com 400, antes de chegar aqui
    @PostMapping("/notificacoes/{id}/degrau")
    @Transactional
    public String corrigir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam Degrau degrau) {
        //Trancando a linha, como no resolver: dois cliques gravam em fila, e o segundo vê o degrau do primeiro
        Notificacao caso = notificacoes.buscarParaAlterar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        //Caso fechado não muda (para corrigir, reabre); tocar no degrau que já está não suja o histórico
        if (!caso.isResolvida() && caso.getDegrau() != degrau) {
            String antes = caso.getDegrau() == null ? "sem degrau" : caso.getDegrau().getNome();
            caso.corrigirDegrau(degrau, usuario.email());
            historico.registrar(caso, usuario.email(), "Corrigiu o degrau: de " + antes + " para " + degrau.getNome());
        }
        //Volta para o cartão do degrau: recarregar a tela não envia de novo
        return "redirect:/notificacoes/" + id + "#titulo-degrau";
    }
}
