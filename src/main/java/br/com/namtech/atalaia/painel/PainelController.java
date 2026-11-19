package br.com.namtech.atalaia.painel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Painel: a tela inicial. Mostra os casos abertos em tres grupos, pelo proximo passo (ver Etapa),
//com o que precisa de gente em cima
@Controller
public class PainelController {

    private final NotificacaoRepository notificacoes;
    private final EmpresaRepository empresas;
    private final AvisoAoClienteRepository avisos;
    private final Clock relogio;

    public PainelController(NotificacaoRepository notificacoes, EmpresaRepository empresas,
            AvisoAoClienteRepository avisos, Clock relogio) {
        this.notificacoes = notificacoes;
        this.empresas = empresas;
        this.avisos = avisos;
        this.relogio = relogio;
    }

    //"ver=resolvidas" troca a lista: em vez dos casos abertos, mostra só os ja resolvidos.
    //A tela inicial fica com o que pede ação; o historico esta a um clique
    @GetMapping("/")
    public String painel(@AuthenticationPrincipal UsuarioLogado usuario,
            @RequestParam(defaultValue = "") String ver, Model model) {
        //Só as notificações do escritório de quem entrou
        List<NotificacaoNaLista> todas = notificacoes.listar(usuario.escritorioId());
        Instant agora = Instant.now(relogio);
        //O aviso ao cliente de cada caso aberto, guardado pelo numero da notificação: decide o grupo do caso e
        //a etiqueta da linha. (um, outro) -> um: dois avisos do mesmo caso criados no mesmo instante viriam os dois;
        //fica um só. Na pratica nao acontece: criar um aviso tranca a notificação, e os pedidos entram em fila
        Map<Long, AvisoAoCliente> avisosDosCasos = avisos.dosCasosAbertos(usuario.escritorioId()).stream()
                .collect(Collectors.toMap(AvisoAoCliente::getNotificacaoId, Function.identity(), (um, outro) -> um));

        //Dentro de cada grupo aberto, o prazo mais perto vem em cima; quem ainda nao tem prazo fica embaixo,
        //na ordem em que chegou (sorted nao embaralha quem empata)
        List<NotificacaoNaLista> abertas = todas.stream().filter(linha -> !linha.resolvida())
                .sorted(Comparator.comparing(NotificacaoNaLista::dataLimite,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        //Separa os abertos pela etapa (de quem é o proximo passo). groupingBy mantem a ordem do prazo dentro de cada grupo
        Map<Etapa, List<NotificacaoNaLista>> porEtapa = abertas.stream().collect(Collectors.groupingBy(
                linha -> Etapa.de(linha, avisosDosCasos.get(linha.id()), agora)));
        List<NotificacaoNaLista> precisaDeGente = porEtapa.getOrDefault(Etapa.PRECISA_DE_GENTE, List.of());
        model.addAttribute("precisaDeGente", precisaDeGente);
        model.addAttribute("esperandoOCliente", porEtapa.getOrDefault(Etapa.ESPERANDO_O_CLIENTE, List.of()));
        model.addAttribute("prontoParaFechar", porEtapa.getOrDefault(Etapa.PRONTO_PARA_FECHAR, List.of()));
        //A etiqueta "o que falta" de cada caso que precisa de gente e ainda nao tem aviso
        Map<Long, String> faltas = new HashMap<>();
        for (NotificacaoNaLista linha : precisaDeGente) {
            String falta = Etapa.oQueFalta(linha, avisosDosCasos.get(linha.id()));
            if (falta != null) {
                faltas.put(linha.id(), falta);
            }
        }
        model.addAttribute("faltas", faltas);

        //As resolvidas mostram a data da resolução, entao a ordem delas segue essa data: a ultima resolvida em cima
        List<NotificacaoNaLista> resolvidas = todas.stream().filter(NotificacaoNaLista::resolvida)
                .sorted(Comparator.comparing(NotificacaoNaLista::resolvidaEm).reversed()).toList();
        model.addAttribute("resolvidas", resolvidas);
        //Sem nenhuma resolvida, "ver=resolvidas" (favorito, link antigo) cai nas abertas: senao a tela ficaria
        //vazia e sem o filtro para voltar
        model.addAttribute("verResolvidas", "resolvidas".equals(ver) && !resolvidas.isEmpty());
        model.addAttribute("nenhuma", todas.isEmpty());
        model.addAttribute("avisos", avisosDosCasos);
        //"agora" serve para saber se o link do aviso venceu; o "hoje" de Teresina, para dizer quantos dias faltam
        model.addAttribute("agora", agora);
        model.addAttribute("hoje", LocalDate.now(relogio));
        //Escritório novo, ainda sem empresa: o primeiro passo é cadastrar uma, e a tela aponta para la
        model.addAttribute("semEmpresa", !empresas.existsByEscritorioId(usuario.escritorioId()));
        return "painel";
    }
}
