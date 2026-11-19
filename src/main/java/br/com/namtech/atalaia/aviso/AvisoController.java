package br.com.namtech.atalaia.aviso;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O lado do escritório no aviso ao cliente: a pessoa escreve o resumo e os documentos, e o Atalaia gera o link.
//Nada vai ao cliente sem ter sido escrito ou aprovado por alguem do escritório
@Controller
public class AvisoController {

    //Os tamanhos das colunas resumo e documentos no banco (V9)
    static final int RESUMO_MAXIMO = 600;
    static final int DOCUMENTOS_MAXIMO = 1000;

    //SecureRandom: sorteio que nao da para prever, proprio para segredo
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final NotificacaoRepository notificacoes;
    private final AvisoAoClienteRepository avisos;
    private final Clock relogio;

    public AvisoController(NotificacaoRepository notificacoes, AvisoAoClienteRepository avisos, Clock relogio) {
        this.notificacoes = notificacoes;
        this.avisos = avisos;
        this.relogio = relogio;
    }

    //Transactional: a tranca do buscarParaAlterar segura um clique duplo, e o aviso antigo e o novo sao gravados juntos
    @PostMapping("/notificacoes/{id}/aviso")
    @Transactional
    public String avisar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id,
            @RequestParam(defaultValue = "") String resumo, @RequestParam(defaultValue = "") String documentos,
            RedirectAttributes recado) {
        //Notificação de outro escritório responde igual a uma que nao existe (404)
        Notificacao notificacao = notificacoes.buscarParaAlterar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        //O # no fim: a tela volta aberta no cartão do aviso
        String volta = "redirect:/notificacoes/" + id + "#titulo-aviso";
        //Caso fechado nao ganha aviso novo
        if (notificacao.isResolvida()) {
            return volta;
        }

        String resumoArrumado = arrumar(resumo);
        String documentosArrumados = arrumar(documentos);
        String campoComErro = "resumo";
        String erro = null;
        if (notificacao.getDataLimite() == null) {
            erro = "Calcule a data limite antes de avisar o cliente: o aviso leva ela junto.";
        } else if (resumoArrumado.codePoints().noneMatch(Character::isLetterOrDigit)
                || resumoArrumado.length() > RESUMO_MAXIMO || temControle(resumoArrumado)) {
            erro = "Escreva o resumo para o cliente, em até " + RESUMO_MAXIMO + " letras.";
        } else if (documentosArrumados.length() > DOCUMENTOS_MAXIMO || temControle(documentosArrumados)) {
            campoComErro = "documentos";
            erro = "A lista de documentos passa de " + DOCUMENTOS_MAXIMO + " letras. Encurte.";
        }
        if (erro != null) {
            recado.addFlashAttribute("erroDoAviso", erro);
            recado.addFlashAttribute("campoDoAvisoComErro", campoComErro);
            //Devolve o que a pessoa escreveu, cortado no dobro do limite: o recado fica guardado na sessão
            recado.addFlashAttribute("resumoDigitado", cortar(resumo, 2 * RESUMO_MAXIMO));
            recado.addFlashAttribute("documentosDigitados", cortar(documentos, 2 * DOCUMENTOS_MAXIMO));
            return volta;
        }

        Instant agora = Instant.now(relogio);
        //O link anterior deixa de confirmar; se ja tinha sido confirmado, fica guardado como registro
        avisos.doCaso(id, usuario.escritorioId()).forEach(antigo -> antigo.substituir(agora));
        avisos.save(new AvisoAoCliente(notificacao, novoToken(), resumoArrumado,
                documentosArrumados.isEmpty() ? null : documentosArrumados, usuario.email(), agora));
        return volta;
    }

    //32 bytes sorteados viram 43 letras que cabem num endereço. Adivinhar um token é tao provavel quanto
    //acertar 256 caras ou coroas seguidas
    private static String novoToken() {
        byte[] bytes = new byte[32];
        SORTEIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    //O navegador envia cada Enter como dois caracteres (\r\n): fica só o \n, e saem as linhas vazias e os espaços das pontas
    private static String arrumar(String texto) {
        return texto.replace("\r\n", "\n").lines().map(String::strip).filter(linha -> !linha.isEmpty())
                .reduce((antes, linha) -> antes + "\n" + linha).orElse("");
    }

    //Caractere de controle (fora a quebra de linha) nao é texto: o banco recusaria com um erro que ninguem entenderia
    private static boolean temControle(String texto) {
        return texto.codePoints().anyMatch(letra -> letra != '\n' && Character.isISOControl(letra));
    }

    private static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
