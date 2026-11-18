package br.com.namtech.atalaia.recepcao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.analista.Analise;
import br.com.namtech.atalaia.analista.Analista;
import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.leitor.PaginasDemais;
import br.com.namtech.atalaia.leitor.ResultadoLeitura;
import br.com.namtech.atalaia.notificacao.Desfecho;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import br.com.namtech.atalaia.verificador.Verificador;
import jakarta.servlet.http.HttpServletResponse;

//Recepção: a porta de entrada. O escritório envia o PDF de uma notificação e o Atalaia le, analisa, confere e guarda
@Controller
public class RecepcaoController {

    //10 MB. Notificação é um PDF de poucas paginas; acima disso é engano ou abuso
    static final long TAMANHO_MAXIMO = 10 * 1024 * 1024;

    //A mesma mensagem serve para as duas travas de tamanho: a daqui e a do servidor (ArquivoGrandeDemais)
    static final String GRANDE_DEMAIS = "Este arquivo é grande demais. O limite é 10 MB. Escolha outro arquivo.";

    //O tamanho da coluna observacao no banco (V4)
    static final int OBSERVACAO_MAXIMA = 500;

    //Todo PDF começa com estes 5 caracteres. Vale mais que o nome do arquivo, que qualquer um troca
    private static final String ASSINATURA_DE_PDF = "%PDF-";

    private final EmpresaRepository empresas;
    private final NotificacaoRepository notificacoes;
    private final LeitorPdf leitor = new LeitorPdf();
    private final Analista analista;
    private final Verificador verificador = new Verificador();

    //O Spring entrega os dois repositorios e a IA (ConfiguracaoDaIA) prontos quando cria o controller
    public RecepcaoController(EmpresaRepository empresas, NotificacaoRepository notificacoes, ModeloDeIA ia) {
        this.empresas = empresas;
        this.notificacoes = notificacoes;
        this.analista = new Analista(ia);
    }

    @GetMapping("/enviar")
    public String formulario(Model model) {
        model.addAttribute("empresas", empresas.findAllByOrderByNome());
        return "enviar";
    }

    @PostMapping("/enviar")
    public String enviar(@RequestParam Long empresaId, @RequestParam MultipartFile arquivo, Model model,
            HttpServletResponse resposta) throws IOException {
        //Empresa que nao existe so chega aqui se alguem mexeu no formulario
        Empresa empresa = empresas.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

        //Confere o tamanho antes de trazer o arquivo para a memoria
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            return recusar(GRANDE_DEMAIS, empresaId, model, resposta);
        }
        byte[] pdf = arquivo.getBytes();
        String recusa = motivoDaRecusa(pdf);
        if (recusa != null) {
            return recusar(recusa, empresaId, model, resposta);
        }

        ResultadoLeitura leitura;
        try {
            leitura = leitor.ler(pdf);
        } catch (PaginasDemais erro) {
            return recusar("Este PDF tem páginas demais. O limite é " + LeitorPdf.MAXIMO_DE_PAGINAS
                    + " páginas. Escolha outro arquivo.", empresaId, model, resposta);
        } catch (IOException erro) {
            //Tem a assinatura de PDF mas o PDFBox nao conseguiu abrir: quebrado ou com senha
            return recusar("Não foi possível abrir este PDF. Ele pode estar corrompido ou protegido por senha. Escolha outro arquivo.",
                    empresaId, model, resposta);
        }

        Notificacao notificacao = notificacoes.save(analisar(empresa, pdf, leitura));
        //Redireciona para a pagina da notificação: recarregar a tela nao envia o arquivo de novo
        return "redirect:/notificacoes/" + notificacao.getId();
    }

    //Transactional: mantem a conversa com o banco aberta durante este metodo, para ler aqui o nome da empresa (LAZY).
    //A tela recebe só o texto pronto; se ela mesma fosse buscar a empresa, a conversa ja estaria fechada
    @GetMapping("/notificacoes/{id}")
    @Transactional(readOnly = true)
    public String notificacao(@PathVariable Long id, Model model) {
        Notificacao notificacao = buscar(id);
        model.addAttribute("notificacao", notificacao);
        model.addAttribute("empresa", notificacao.getEmpresa().getNome());
        model.addAttribute("recebidaEm", Notificacao.DIA_E_HORA.format(notificacao.getRecebidaEm()));
        //Os dados tambem sao buscados só quando alguem pede: a copia traz todos agora, com a conversa aberta
        model.addAttribute("dados", List.copyOf(notificacao.getDados()));
        model.addAttribute("desfechos", Desfecho.values());
        if (notificacao.isResolvida()) {
            model.addAttribute("resolvidaEm", Notificacao.DIA_E_HORA.format(notificacao.getResolvidaEm()));
        }
        return "notificacao";
    }

    //Marca a notificação como resolvida, com o desfecho e a observação de quem resolveu.
    //Transactional (sem readOnly): a mudança feita no objeto é gravada no banco quando o metodo termina
    @PostMapping("/notificacoes/{id}/resolver")
    @Transactional
    public String resolver(@PathVariable Long id, @RequestParam(required = false) Desfecho desfecho,
            @RequestParam(defaultValue = "") String observacao, Model model, HttpServletResponse resposta) {
        Notificacao notificacao = buscar(id);
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
                return notificacao(id, model);
            }
            notificacao.resolver(desfecho, texto);
        }
        //Redireciona: recarregar a tela nao envia o formulario de novo
        return "redirect:/notificacoes/" + id;
    }

    //Entrega o PDF original para a tela mostrar ao lado do texto lido
    @GetMapping("/notificacoes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"notificacao-" + id + ".pdf\"")
                .body(buscar(id).getArquivo());
    }

    private Notificacao buscar(Long id) {
        return notificacoes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
        return new Notificacao(empresa, pdf, leitura.texto(), analise.tipo(), analise.dados(), motivos);
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
    private String recusar(String mensagem, Long empresaId, Model model, HttpServletResponse resposta) {
        resposta.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("erro", mensagem);
        model.addAttribute("empresaEscolhida", empresaId);
        return formulario(model);
    }
}
