package br.com.namtech.atalaia.aviso;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.namtech.atalaia.notificacao.Empresa;

//O lado do cliente: a pagina que o dono da empresa abre pelo link, sem login (liberada na ConfiguracaoDeSeguranca).
//Quem tem o token ve um aviso só: o resumo, o prazo e os documentos. Nada do resto do Atalaia aparece aqui
@Controller
public class ClienteController {

    private final AvisoAoClienteRepository avisos;
    private final Clock relogio;

    public ClienteController(AvisoAoClienteRepository avisos, Clock relogio) {
        this.avisos = avisos;
        this.relogio = relogio;
    }

    //Transactional: mantem a conversa com o banco aberta para ler aqui o nome da empresa e do escritório (LAZY)
    @GetMapping("/c/{token}")
    @Transactional(readOnly = true)
    public String aviso(@PathVariable String token, Model model) {
        //Token que nao existe responde 404, sem dizer mais nada
        AvisoAoCliente aviso = avisos.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Empresa empresa = aviso.getNotificacao().getEmpresa();
        model.addAttribute("aviso", aviso);
        model.addAttribute("situacao", aviso.situacao(Instant.now(relogio)).name());
        model.addAttribute("empresa", empresa.getNome());
        model.addAttribute("escritorio", empresa.getEscritorio().getNome());
        return "cliente";
    }

    //O registro de ciência. A resposta precisa ser uma das tres datas mostradas; errar a data nao impede confirmar
    @PostMapping("/c/{token}")
    @Transactional
    public String confirmar(@PathVariable String token, @RequestParam(defaultValue = "") String resposta,
            RedirectAttributes recado) {
        AvisoAoCliente aviso = avisos.buscarParaConfirmar(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Instant agora = Instant.now(relogio);
        //Ja confirmado, substituido ou vencido: nada muda, e a pagina explica a situação
        if (aviso.situacao(agora) == SituacaoDoAviso.AGUARDANDO) {
            LocalDate marcada = data(resposta);
            if (marcada == null || !aviso.opcoesDePrazo().contains(marcada)) {
                recado.addFlashAttribute("erro", "Marque uma das datas para confirmar.");
            } else {
                aviso.confirmar(marcada, agora);
            }
        }
        //Redireciona: recarregar a pagina nao envia a resposta de novo
        return "redirect:/c/" + aviso.getToken();
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException erro) {
            return null;
        }
    }
}
