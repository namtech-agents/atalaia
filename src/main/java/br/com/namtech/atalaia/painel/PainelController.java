package br.com.namtech.atalaia.painel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
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
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.NotificacaoNaLista;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.prazo.RegraCadastradaRepository;

//Painel: a tela inicial. Mostra os casos abertos em tres grupos, pelo proximo passo (ver Etapa),
//com o que precisa de gente em cima
@Controller
public class PainelController {

    private final NotificacaoRepository notificacoes;
    private final EmpresaRepository empresas;
    private final AvisoAoClienteRepository avisos;
    private final RegraCadastradaRepository regras;
    private final Clock relogio;

    public PainelController(NotificacaoRepository notificacoes, EmpresaRepository empresas,
            AvisoAoClienteRepository avisos, RegraCadastradaRepository regras, Clock relogio) {
        this.notificacoes = notificacoes;
        this.empresas = empresas;
        this.avisos = avisos;
        this.regras = regras;
        this.relogio = relogio;
    }

    //"ver=resolvidas" troca a lista: em vez dos casos abertos, mostra só os ja resolvidos.
    //A tela inicial fica com o que pede ação; o historico esta a um clique.
    //"empresa" filtra os casos de um cliente só (o resto da tela funciona igual, só com menos linhas)
    @GetMapping("/")
    public String painel(@AuthenticationPrincipal UsuarioLogado usuario,
            @RequestParam(defaultValue = "") String ver, @RequestParam(defaultValue = "") String empresa,
            @RequestParam(defaultValue = "") String q, Model model) {
        //Só as notificações do escritório de quem entrou
        List<NotificacaoNaLista> doEscritorio = notificacoes.listar(usuario.escritorioId());
        List<Empresa> clientes = empresas.findAllByEscritorioIdOrderByNome(usuario.escritorioId());
        //Quantos casos abertos cada cliente tem, contados antes do filtro: a escolha do cliente mostra o numero de todos
        Map<Long, Long> abertasPorCliente = doEscritorio.stream().filter(linha -> !linha.resolvida())
                .collect(Collectors.groupingBy(NotificacaoNaLista::empresaId, Collectors.counting()));
        //Só vale filtrar por uma empresa do proprio escritório. Numero de outra, ou texto (endereço mexido), é ignorado
        //(chega como texto para "abc" nao virar erro 400):
        //a tela mostra todos os clientes, e nada da outra empresa aparece, porque a lista já é só do escritório
        Long filtro = clientes.stream().map(Empresa::getId).filter(id -> id.toString().equals(empresa)).findFirst().orElse(null);
        List<NotificacaoNaLista> doCliente = filtro == null ? doEscritorio
                : doEscritorio.stream().filter(linha -> linha.empresaId().equals(filtro)).toList();
        //A busca (#215): pelo nome da empresa, pelo CNPJ (só os números contam) ou pelo tipo do documento.
        //Sem acento e sem maiúscula: "joao" acha "João". Texto vazio não filtra nada
        String busca = semAcento(q.strip());
        String numerosDaBusca = q.replaceAll("\\D", "");
        Map<Long, String> cnpjs = clientes.stream().collect(Collectors.toMap(Empresa::getId, Empresa::getCnpj));
        List<NotificacaoNaLista> todas = busca.isEmpty() ? doCliente : doCliente.stream()
                .filter(linha -> semAcento(linha.empresa()).contains(busca)
                        || (linha.tipo() != null && semAcento(linha.tipo().getNome()).contains(busca))
                        || (numerosDaBusca.length() >= 3 && cnpjs.getOrDefault(linha.empresaId(), "").contains(numerosDaBusca)))
                .toList();
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
        //"nenhuma" fala do escritório (nunca recebeu nada); "nadaDoCliente", do filtro (o cliente escolhido nao tem casos)
        model.addAttribute("nenhuma", doEscritorio.isEmpty());
        model.addAttribute("nadaDoCliente", filtro != null && busca.isEmpty() && todas.isEmpty());
        model.addAttribute("busca", q.strip());
        model.addAttribute("nadaNaBusca", !busca.isEmpty() && todas.isEmpty());
        model.addAttribute("abertasNoTotal", abertas.size());
        model.addAttribute("clientes", clientes);
        model.addAttribute("abertasPorCliente", abertasPorCliente);
        model.addAttribute("filtro", filtro);
        model.addAttribute("avisos", avisosDosCasos);
        //"agora" serve para saber se o link do aviso venceu; o "hoje" de Teresina, para dizer quantos dias faltam
        model.addAttribute("agora", agora);
        model.addAttribute("hoje", LocalDate.now(relogio));
        //Escritório novo, ainda sem empresa: o primeiro passo é cadastrar uma, e a tela aponta para la
        model.addAttribute("semEmpresa", clientes.isEmpty());
        //"Para começar" (#215): os três passos que uma conta nova precisa dar, na ordem. Sem regra de prazo não sai
        //prazo nem aviso, e antes isso só aparecia dentro do caso. Some quando os três estão feitos
        boolean temRegra = !regras.findAllByEscritorioIdOrderByNome(usuario.escritorioId()).isEmpty();
        model.addAttribute("temRegra", temRegra);
        model.addAttribute("paraComecar", clientes.isEmpty() || !temRegra || doEscritorio.isEmpty());
        return "painel";
    }

    //Para comparar textos sem ligar para acento nem maiúscula
    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
