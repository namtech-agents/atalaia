package br.com.namtech.atalaia.recepcao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

import br.com.namtech.atalaia.leitor.LeitorPdf;
import br.com.namtech.atalaia.leitor.ResultadoLeitura;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;
import jakarta.servlet.http.HttpServletResponse;

//Recepção: a porta de entrada. O escritório envia o PDF de uma notificação e o Atalaia le e guarda
@Controller
public class RecepcaoController {

    //10 MB. Notificação é um PDF de poucas paginas; acima disso é engano ou abuso
    static final long TAMANHO_MAXIMO = 10 * 1024 * 1024;

    //Todo PDF começa com estes 5 caracteres. Vale mais que o nome do arquivo, que qualquer um troca
    private static final String ASSINATURA_DE_PDF = "%PDF-";

    //Data e hora como se escreve aqui, no horario de Teresina
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Fortaleza"));

    private final EmpresaRepository empresas;
    private final NotificacaoRepository notificacoes;
    private final LeitorPdf leitor = new LeitorPdf();

    //O Spring entrega os dois repositorios prontos quando cria o controller
    public RecepcaoController(EmpresaRepository empresas, NotificacaoRepository notificacoes) {
        this.empresas = empresas;
        this.notificacoes = notificacoes;
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

        String recusa = motivoDaRecusa(arquivo);
        if (recusa != null) {
            return recusar(recusa, empresaId, model, resposta);
        }

        byte[] pdf = arquivo.getBytes();
        ResultadoLeitura leitura;
        try {
            leitura = leitor.ler(pdf);
        } catch (IOException erro) {
            //Tem a assinatura de PDF mas o PDFBox nao conseguiu abrir: quebrado ou com senha
            return recusar("Não foi possível abrir este PDF. Ele pode estar corrompido ou protegido por senha.",
                    empresaId, model, resposta);
        }

        Notificacao notificacao = notificacoes.save(
                new Notificacao(empresa, pdf, leitura.texto(), leitura.precisaDeRevisao()));
        //Redireciona para a pagina da notificação: recarregar a tela nao envia o arquivo de novo
        return "redirect:/notificacoes/" + notificacao.getId();
    }

    //Transactional: mantem a conversa com o banco aberta ate o fim, para a tela conseguir ler a empresa (LAZY)
    @GetMapping("/notificacoes/{id}")
    @Transactional(readOnly = true)
    public String notificacao(@PathVariable Long id, Model model) {
        Notificacao notificacao = buscar(id);
        model.addAttribute("notificacao", notificacao);
        model.addAttribute("empresa", notificacao.getEmpresa().getNome());
        model.addAttribute("recebidaEm", DIA_E_HORA.format(notificacao.getRecebidaEm()));
        return "notificacao";
    }

    //Entrega o PDF original para a tela mostrar ao lado do texto lido
    @GetMapping("/notificacoes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                //nosniff: o navegador trata como PDF e nada mais, mesmo que o conteudo pareça outra coisa
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"notificacao-" + id + ".pdf\"")
                .body(buscar(id).getArquivo());
    }

    private Notificacao buscar(Long id) {
        return notificacoes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    //Devolve a mensagem para a pessoa, ou null se o arquivo pode seguir
    private String motivoDaRecusa(MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) {
            return "Escolha um arquivo PDF para enviar.";
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            return "Este arquivo é grande demais. O limite é 10 MB.";
        }
        byte[] comeco = arquivo.getInputStream().readNBytes(ASSINATURA_DE_PDF.length());
        if (!ASSINATURA_DE_PDF.equals(new String(comeco, StandardCharsets.US_ASCII))) {
            return "Este arquivo não é um PDF. Envie a notificação em PDF.";
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
