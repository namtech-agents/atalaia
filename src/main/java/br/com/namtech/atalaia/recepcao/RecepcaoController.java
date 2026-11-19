package br.com.namtech.atalaia.recepcao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.namtech.atalaia.analista.Analise;
import br.com.namtech.atalaia.analista.Analista;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.aviso.AvisoAoCliente;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
import br.com.namtech.atalaia.aviso.SituacaoDoAviso;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.leitor.PaginasDemais;
import br.com.namtech.atalaia.leitor.ResultadoLeitura;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.prazo.FeriadosNacionais;
import br.com.namtech.atalaia.prazo.RegraCadastradaRepository;
import br.com.namtech.atalaia.verificador.Verificador;
import jakarta.servlet.http.HttpServletResponse;

//Recepção: a porta de entrada. O escritório envia o PDF de uma notificação e o Atalaia le, analisa, confere e guarda
@Controller
public class RecepcaoController {

    //10 MB. Notificação é um PDF de poucas paginas; acima disso é engano ou abuso
    static final long TAMANHO_MAXIMO = 10 * 1024 * 1024;

    //Acima de 15 MB quem barra é o servidor, antes de chegar aqui: a pessoa ve a pagina de erro (error.html, caso 413)
    static final String GRANDE_DEMAIS = "Este arquivo é grande demais. O limite é 10 MB. Escolha outro arquivo.";

    //O tamanho da coluna observacao no banco (V4)
    static final int OBSERVACAO_MAXIMA = 500;

    //Todo PDF começa com estes 5 caracteres. Vale mais que o nome do arquivo, que qualquer um troca
    private static final String ASSINATURA_DE_PDF = "%PDF-";

    private final EmpresaRepository empresas;
    private final NotificacaoRepository notificacoes;
    private final RegraCadastradaRepository regras;
    private final AvisoAoClienteRepository avisos;
    private final Clock relogio;
    private final String enderecoPublico;
    private final LeitorPdf leitor = new LeitorPdf();
    private final Analista analista;
    private final Verificador verificador = new Verificador();
    private final Historico historico;
    private final TransactionTemplate transacao;

    //O Spring entrega os repositorios e a IA (ConfiguracaoDaIA) prontos quando cria o controller
    public RecepcaoController(EmpresaRepository empresas, NotificacaoRepository notificacoes,
            RegraCadastradaRepository regras, AvisoAoClienteRepository avisos, Clock relogio, ModeloDeIA ia,
            Historico historico, TransactionTemplate transacao,
            @Value("${atalaia.endereco-publico:}") String enderecoPublico) {
        //Sem a barra do fim, para montar o link sempre do mesmo jeito
        this.enderecoPublico = enderecoPublico.strip().replaceAll("/+$", "");
        this.empresas = empresas;
        this.notificacoes = notificacoes;
        this.regras = regras;
        this.avisos = avisos;
        this.relogio = relogio;
        this.analista = new Analista(ia);
        this.historico = historico;
        this.transacao = transacao;
    }

    //AuthenticationPrincipal: o Spring Security entrega quem esta com a sessão aberta.
    //Todo metodo daqui usa o escritório dessa pessoa nas buscas: é o que separa um escritório do outro
    @GetMapping("/enviar")
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        model.addAttribute("empresas", empresas.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        return "enviar";
    }

    @PostMapping("/enviar")
    public String enviar(@AuthenticationPrincipal UsuarioLogado usuario, @RequestParam Long empresaId,
            @RequestParam MultipartFile arquivo, Model model, HttpServletResponse resposta) throws IOException {
        //Empresa que nao existe, ou que é de outro escritório, so chega aqui se alguem mexeu no formulario
        Empresa empresa = empresas.findByIdAndEscritorioId(empresaId, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

        //Confere o tamanho antes de trazer o arquivo para a memoria
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            return recusar(GRANDE_DEMAIS, usuario, empresaId, model, resposta);
        }
        byte[] pdf = arquivo.getBytes();
        String recusa = motivoDaRecusa(pdf);
        if (recusa != null) {
            return recusar(recusa, usuario, empresaId, model, resposta);
        }

        ResultadoLeitura leitura;
        try {
            leitura = leitor.ler(pdf);
        } catch (PaginasDemais erro) {
            return recusar("Este PDF tem páginas demais. O limite é " + LeitorPdf.MAXIMO_DE_PAGINAS
                    + " páginas. Escolha outro arquivo.", usuario, empresaId, model, resposta);
        } catch (IOException erro) {
            //Tem a assinatura de PDF mas o PDFBox nao conseguiu abrir: quebrado ou com senha
            return recusar("Não foi possível abrir este PDF. Ele pode estar corrompido ou protegido por senha. Escolha outro arquivo.",
                    usuario, empresaId, model, resposta);
        }

        //A IA responde fora da transação: segurar a conexão com o banco enquanto ela pensa travaria os outros pedidos
        Notificacao analisada = analisar(empresa, pdf, leitura);
        //A notificação e o evento "Enviou o PDF" sao gravados juntos (o metodo nao é Transactional por causa da IA):
        //se o evento falhar, a notificação tambem nao fica, e a pessoa pode enviar de novo sem duplicar o caso
        Notificacao notificacao = transacao.execute(conversa -> {
            Notificacao gravada = notificacoes.save(analisada);
            historico.registrar(gravada, usuario.email(), "Enviou o PDF");
            return gravada;
        });
        //Redireciona para a pagina da notificação: recarregar a tela nao envia o arquivo de novo
        return "redirect:/notificacoes/" + notificacao.getId();
    }

    //Transactional: mantem a conversa com o banco aberta durante este metodo, para ler aqui o nome da empresa (LAZY).
    //A tela recebe só o texto pronto; se ela mesma fosse buscar a empresa, a conversa ja estaria fechada
    @GetMapping("/notificacoes/{id}")
    @Transactional(readOnly = true)
    public String notificacao(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id, Model model) {
        Notificacao notificacao = buscar(id, usuario);
        model.addAttribute("notificacao", notificacao);
        model.addAttribute("empresa", notificacao.getEmpresa().getNome());
        model.addAttribute("recebidaEm", Notificacao.DIA_E_HORA.format(notificacao.getRecebidaEm()));
        //Os dados tambem sao buscados só quando alguem pede: a copia traz todos agora, com a conversa aberta
        model.addAttribute("dados", List.copyOf(notificacao.getDados()));
        model.addAttribute("desfechos", Desfecho.values());
        //As regras do escritório, para o cartão do prazo (quem calcula é o PrazoController)
        model.addAttribute("regras", regras.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        //O campo da ciência nao deixa escolher dia no futuro; o aviso dos feriados fica logo abaixo da data
        model.addAttribute("hoje", LocalDate.now(relogio));
        model.addAttribute("avisoDosFeriados", FeriadosNacionais.AVISO);
        if (notificacao.getPrazoCalculadoEm() != null) {
            model.addAttribute("prazoCalculadoEm", Notificacao.DIA_E_HORA.format(notificacao.getPrazoCalculadoEm()));
        }
        if (notificacao.isResolvida()) {
            model.addAttribute("resolvidaEm", Notificacao.DIA_E_HORA.format(notificacao.getResolvidaEm()));
        }
        avisoAoCliente(notificacao, usuario, model);
        model.addAttribute("historico", historico.doCaso(id, usuario.escritorioId()));
        return "notificacao";
    }

    //O cartão "Aviso ao cliente": o aviso que vale (o mais novo), o link dele e se da para fazer outro.
    //Quem cria o aviso é o AvisoController; quem confirma é o cliente, no ClienteController
    private void avisoAoCliente(Notificacao notificacao, UsuarioLogado usuario, Model model) {
        List<AvisoAoCliente> doCaso = avisos.doCaso(notificacao.getId(), usuario.escritorioId());
        AvisoAoCliente aviso = doCaso.isEmpty() ? null : doCaso.get(0);
        boolean prazoMudou = false;
        boolean confirmado = false;
        if (aviso != null) {
            SituacaoDoAviso situacao = aviso.situacao(Instant.now(relogio));
            confirmado = situacao == SituacaoDoAviso.CONFIRMADO;
            //O cliente viu uma data e a conta foi refeita depois: o aviso ficou velho
            prazoMudou = aviso.prazoMudou(notificacao.getDataLimite());
            model.addAttribute("aviso", aviso);
            model.addAttribute("situacaoDoAviso", situacao);
            //O endereço completo, pronto para copiar. No ar vem da configuração (atalaia.endereco-publico):
            //o endereço que chega no pedido pode ser o interno do servidor, ou ate forjado por quem pede.
            //No Mac, sem a configuração, usa o endereço de onde o Atalaia esta sendo acessado
            String base = enderecoPublico.isBlank()
                    ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : enderecoPublico;
            model.addAttribute("linkDoAviso", base + "/c/" + aviso.getToken());
            //Registros de ciência de avisos anteriores (o cliente confirmou e depois a data mudou): continuam à vista
            model.addAttribute("registrosAnteriores", doCaso.stream().skip(1)
                    .filter(antigo -> antigo.getConfirmadoEmPorExtenso() != null).toList());
        }
        model.addAttribute("prazoMudou", prazoMudou);
        //Da para avisar com o caso aberto e a data limite calculada. Depois que o cliente confirmou,
        //só faz sentido um aviso novo se a data mudou
        model.addAttribute("podeAvisar", !notificacao.isResolvida() && notificacao.getDataLimite() != null
                && (!confirmado || prazoMudou));
    }

    //Marca a notificação como resolvida, com o desfecho e a observação de quem resolveu.
    //Transactional (sem readOnly): a mudança feita no objeto é gravada no banco quando o metodo termina
    @PostMapping("/notificacoes/{id}/resolver")
    @Transactional
    public String resolver(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam(required = false) Desfecho desfecho, @RequestParam(defaultValue = "") String observacao,
            Model model, HttpServletResponse resposta) {
        //Busca trancando a linha: se dois pedidos chegarem juntos, o segundo espera aqui o primeiro gravar
        Notificacao notificacao = notificacoes.buscarParaAlterar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        //Ja resolvida (clique duplo, ou a pagina ficou aberta em duas abas): nao troca o que foi registrado
        if (!notificacao.isResolvida()) {
            //O navegador envia cada Enter como dois caracteres (\r\n), mas o limite da tela conta um:
            //trocando por \n, a conta daqui fica igual a que a pessoa ve
            String texto = observacao.replace("\r\n", "\n").strip();
            //Observação sem nenhuma letra ou numero (só espaços, inclusive os invisiveis) nao diz nada
            boolean semConteudo = texto.codePoints().noneMatch(Character::isLetterOrDigit);
            if (desfecho == null || semConteudo || texto.length() > OBSERVACAO_MAXIMA) {
                resposta.setStatus(HttpStatus.BAD_REQUEST.value());
                //A mensagem fala do campo que falhou, e a tela marca esse campo (campoComErro)
                model.addAttribute("campoComErro", desfecho == null ? "desfecho" : "observacao");
                model.addAttribute("erro", desfecho == null ? "Escolha o desfecho."
                        : "Escreva o que foi feito, em até " + OBSERVACAO_MAXIMA + " letras.");
                model.addAttribute("observacao", observacao);
                model.addAttribute("desfechoEscolhido", desfecho);
                return notificacao(usuario, id, model);
            }
            notificacao.resolver(desfecho, texto);
            historico.registrar(notificacao, usuario.email(), "Resolveu o caso. Desfecho: " + desfecho.getNome());
        }
        //Redireciona: recarregar a tela nao envia o formulario de novo
        return "redirect:/notificacoes/" + id;
    }

    //Entrega o PDF original para a tela mostrar ao lado do texto lido
    @GetMapping("/notificacoes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"notificacao-" + id + ".pdf\"")
                .body(buscar(id, usuario).getArquivo());
    }

    //Notificação de outro escritório responde igual a uma que nao existe (404): nem a existencia dela vaza
    private Notificacao buscar(Long id, UsuarioLogado usuario) {
        return notificacoes.buscar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    //O caminho do documento depois de lido: Analista (IA) e Verificador (codigo). Cada peça que desconfia
    //acrescenta o seu motivo; com um motivo que seja, a notificação vai para uma pessoa
    private Notificacao analisar(Empresa empresa, byte[] pdf, ResultadoLeitura leitura) {
        if (leitura.precisaDeRevisao()) {
            //Sem texto nao ha o que mandar para a IA
            return new Notificacao(empresa, pdf, leitura.texto(), null, List.of(),
                    List.of("Este PDF não tem texto que dê para ler (parece escaneado)."));
        }
        Analise analise = analista.analisar(leitura.texto());
        List<String> motivos = new ArrayList<>();
        if (analise.motivo() != null) {
            motivos.add(analise.motivo());
        }
        //So confere os dados se a IA chegou a classificar; senao o motivo dela ja diz tudo
        if (analise.tipo() != null) {
            motivos.addAll(verificador.conferir(leitura.texto(), analise.dados()).motivos());
        }
        //"Informativo" é o unico tipo que ninguem precisa responder: se o texto fala em prazo ou multa, uma pessoa confere
        if (analise.tipo() == TipoDeDocumento.INFORMATIVO && verificador.falaEmObrigacao(leitura.texto())) {
            motivos.add("A IA classificou como informativo, mas o texto fala em prazo, intimação, citação ou multa.");
        }
        Notificacao notificacao = new Notificacao(empresa, pdf, leitura.texto(), analise.tipo(), analise.dados(), motivos);
        notificacao.sugerirResumo(analise.resumoSugerido());
        return notificacao;
    }

    //Devolve a mensagem para a pessoa, ou null se o arquivo pode seguir
    private String motivoDaRecusa(byte[] arquivo) {
        if (arquivo.length == 0) {
            return "Escolha um arquivo PDF para enviar.";
        }
        //Compara só o começo do arquivo com a assinatura (US_ASCII: um byte vira uma letra)
        String comeco = new String(arquivo, 0, Math.min(arquivo.length, ASSINATURA_DE_PDF.length()),
                StandardCharsets.US_ASCII);
        if (!comeco.equals(ASSINATURA_DE_PDF)) {
            return "Este arquivo não é um PDF. Escolha a notificação em PDF.";
        }
        return null;
    }

    //Volta para o formulario com a mensagem, mantendo a empresa escolhida, e responde 400 (pedido recusado)
    private String recusar(String mensagem, UsuarioLogado usuario, Long empresaId, Model model,
            HttpServletResponse resposta) {
        resposta.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("erro", mensagem);
        model.addAttribute("empresaEscolhida", empresaId);
        return formulario(usuario, model);
    }
}
