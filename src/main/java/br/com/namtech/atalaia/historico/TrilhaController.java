package br.com.namtech.atalaia.historico;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.PdfDeTexto;
import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O PDF da trilha do caso (US27): cada registro com a assinatura, o resultado da conferência e o selo
//(a assinatura do último registro). Quem guarda o PDF guarda o selo: se depois alguém mexer no banco,
//o selo do PDF não bate mais com a trilha da tela
@Controller
public class TrilhaController {

    private final NotificacaoRepository notificacoes;
    private final Historico historico;
    private final Clock relogio;

    public TrilhaController(NotificacaoRepository notificacoes, Historico historico, Clock relogio) {
        this.notificacoes = notificacoes;
        this.historico = historico;
        this.relogio = relogio;
    }

    //Transactional (só leitura): o nome da empresa é buscado no banco durante a montagem do PDF
    @GetMapping("/notificacoes/{id}/trilha")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> trilha(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable Long id)
            throws IOException {
        //De outro escritório responde igual a um caso que não existe (404)
        Notificacao caso = notificacoes.buscar(id, usuario.escritorioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<EventoDoCaso> eventos = historico.doCaso(id, usuario.escritorioId());
        Trilha trilha = Trilha.conferir(eventos);

        List<String> linhas = new ArrayList<>();
        linhas.add("Empresa: " + caso.getEmpresa().getNome() + " (CNPJ " + caso.getEmpresa().getCnpjFormatado() + ")");
        linhas.add("Gerado em " + Notificacao.DIA_E_HORA.format(Instant.now(relogio)) + " por " + usuario.email());
        linhas.add("");
        linhas.add(!trilha.temAssinatura() ? "Este caso ainda não tem registros assinados."
                : trilha.integra() ? "Conferência: todas as assinaturas conferem. Nenhum registro foi alterado depois de gravado."
                : "Conferência: a trilha NÃO confere a partir do registro de " + trilha.quebra().getQuandoPorExtenso()
                        + ". Um registro foi alterado ou apagado depois de gravado.");
        linhas.add("Cada registro leva a assinatura do anterior (SHA-256). Mudar um registro antigo muda todas as"
                + " assinaturas seguintes.");
        for (EventoDoCaso evento : eventos) {
            linhas.add("");
            linhas.add(evento.getQuandoPorExtenso() + (evento.getQuem() == null ? "" : " · " + evento.getQuem()));
            linhas.add(evento.getAcao());
            linhas.add(evento.getAssinatura() == null ? "Sem assinatura (registro de antes da trilha)"
                    : "Assinatura: " + evento.getAssinatura());
        }
        if (trilha.selo() != null) {
            linhas.add("");
            linhas.add("Selo da trilha (assinatura do último registro): " + trilha.selo());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"trilha-do-caso-" + id + ".pdf\"")
                .body(PdfDeTexto.montar("Trilha do caso nº " + id, linhas));
    }
}
