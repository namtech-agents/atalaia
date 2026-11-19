package br.com.namtech.atalaia.simulado;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.namtech.atalaia.PdfDeTexto;
import br.com.namtech.atalaia.aviso.AvisoAoClienteRepository;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.historico.Historico;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.EmpresaRepository;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//Simulado com cronômetro (US30): o dono solta uma notificação de teste e vê quanto tempo a equipe levou para abrir,
//avisar o cliente e fechar. O caso entra na lista como qualquer outro; só conta que era teste quando termina
@Controller
public class SimuladoController {

    //O documento de teste. Parece uma notificação comum, para a equipe tratar como trataria uma de verdade,
    //mas a última linha diz que é exercício: se o PDF sair do escritório, ninguém confunde com um documento real
    static final String TITULO = "Notificação para apresentação de documentos";
    static final List<String> LINHAS = List.of(
            "Órgão: Secretaria da Fazenda",
            "",
            "Fica a empresa notificada a apresentar, no prazo de 10 (dez) dias contados da ciência,",
            "os livros fiscais e os comprovantes de recolhimento do último trimestre.",
            "O não atendimento pode resultar em auto de infração.",
            "",
            "Documento de exercício gerado pelo Atalaia (simulado do escritório). Não tem valor legal.");

    private final EmpresaRepository empresas;
    private final NotificacaoRepository notificacoes;
    private final AvisoAoClienteRepository avisos;
    private final Historico historico;

    public SimuladoController(EmpresaRepository empresas, NotificacaoRepository notificacoes,
            AvisoAoClienteRepository avisos, Historico historico) {
        this.empresas = empresas;
        this.notificacoes = notificacoes;
        this.avisos = avisos;
        this.historico = historico;
    }

    //A tela: o que é o simulado, o botão e os tempos dos simulados anteriores.
    //Transactional (só leitura): o nome da empresa de cada simulado é buscado no banco aqui
    @GetMapping("/simulado")
    @Transactional(readOnly = true)
    public String tela(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        model.addAttribute("empresas", empresas.findAllByEscritorioIdOrderByNome(usuario.escritorioId()));
        List<TemposDoSimulado> todos = notificacoes.simulados(usuario.escritorioId()).stream()
                .map(teste -> TemposDoSimulado.de(teste, avisos.doCaso(teste.getId(), usuario.escritorioId())))
                .toList();
        //O simulado em andamento entra só na contagem, sem empresa e sem link: quem abre esta tela não descobre
        //qual caso da lista é o teste. Os terminados mostram tudo
        model.addAttribute("terminados", todos.stream().filter(TemposDoSimulado::terminou).toList());
        model.addAttribute("emAndamento", todos.stream().filter(tempos -> !tempos.terminou()).count());
        return "simulado";
    }

    //Solta o simulado: um caso novo na lista, para a empresa escolhida, com o evento no histórico
    @PostMapping("/simulado")
    @Transactional
    public String soltar(@AuthenticationPrincipal UsuarioLogado usuario, @RequestParam Long empresaId,
            RedirectAttributes recado) throws IOException {
        //Empresa de outro escritório (ou que não existe) só chega aqui se alguém mexeu no formulário
        Empresa empresa = empresas.findByIdAndEscritorioId(empresaId, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        byte[] pdf = PdfDeTexto.montar(TITULO, LINHAS);
        Notificacao teste = notificacoes.save(Notificacao.simulado(empresa, pdf, TITULO + "\n" + String.join("\n", LINHAS),
                usuario.email()));
        historico.registrar(teste, usuario.email(), "Soltou um simulado");
        recado.addFlashAttribute("solto", "Simulado solto para " + empresa.getNome()
                + ". Ele já está na lista de notificações. Os tempos aparecem aqui quando a equipe gerar o aviso ao cliente ou fechar o caso.");
        //Redireciona: recarregar a tela não solta outro
        return "redirect:/simulado";
    }
}
