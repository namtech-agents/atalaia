package br.com.namtech.atalaia.painel;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Painel: a tela inicial. Mostra as notificações recebidas, com o que precisa de gente em cima
@Controller
public class PainelController {

    private final NotificacaoRepository notificacoes;

    public PainelController(NotificacaoRepository notificacoes) {
        this.notificacoes = notificacoes;
    }

    @GetMapping("/")
    public String painel(Model model) {
        List<NotificacaoNaLista> todas = notificacoes.listar();
        //Dois grupos, na ordem em que a pessoa deve olhar: primeiro o que o Atalaia nao conseguiu conferir sozinho
        model.addAttribute("precisaDeGente", todas.stream().filter(NotificacaoNaLista::precisaDeRevisao).toList());
        model.addAttribute("conferidas", todas.stream().filter(linha -> !linha.precisaDeRevisao()).toList());
        return "painel";
    }
}
