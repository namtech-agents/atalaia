package br.com.namtech.atalaia.painel;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Painel: a tela inicial. Mostra as notificações recebidas, com o que precisa de gente em cima
@Controller
public class PainelController {

    private final NotificacaoRepository notificacoes;
    private final EmpresaRepository empresas;

    public PainelController(NotificacaoRepository notificacoes, EmpresaRepository empresas) {
        this.notificacoes = notificacoes;
        this.empresas = empresas;
    }

    @GetMapping("/")
    public String painel(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        //Só as notificações do escritório de quem entrou
        List<NotificacaoNaLista> todas = notificacoes.listar(usuario.escritorioId());
        //Tres grupos, na ordem em que a pessoa deve olhar: primeiro o que o Atalaia nao conseguiu conferir sozinho,
        //depois o que esta conferido e aberto, e por ultimo o que ja foi resolvido
        List<NotificacaoNaLista> abertas = todas.stream().filter(linha -> !linha.resolvida()).toList();
        model.addAttribute("precisaDeGente", abertas.stream().filter(NotificacaoNaLista::precisaDeRevisao).toList());
        model.addAttribute("conferidas", abertas.stream().filter(linha -> !linha.precisaDeRevisao()).toList());
        //As resolvidas mostram a data da resolução, entao a ordem delas segue essa data: a ultima resolvida em cima
        model.addAttribute("resolvidas", todas.stream().filter(NotificacaoNaLista::resolvida)
                .sorted(Comparator.comparing(NotificacaoNaLista::resolvidaEm).reversed()).toList());
        //Escritório novo, ainda sem empresa: o primeiro passo é cadastrar uma, e a tela aponta para la
        model.addAttribute("semEmpresa", !empresas.existsByEscritorioId(usuario.escritorioId()));
        return "painel";
    }
}
