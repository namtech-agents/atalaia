package br.com.namtech.atalaia.historico;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import br.com.namtech.atalaia.conta.UsuarioLogado;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.notificacao.NotificacaoRepository;

//O PDF da trilha do caso (US27): cada registro com a assinatura, o resultado da conferência e o selo
//(a assinatura do último registro). Quem guarda o PDF guarda o selo: se depois alguém mexer no banco,
//o selo do PDF não bate mais com a trilha da tela
@Controller
public class TrilhaController {

    //Página A4, margem de 50 pontos (uns 18 mm) e corpo de 10 pontos
    private static final float MARGEM = 50;
    private static final float CORPO = 10;
    private static final float ENTRELINHA = 14;

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
                .body(montarPdf("Trilha do caso nº " + id, linhas));
    }

    //Escreve o título e as linhas, quebrando cada uma pela largura da página e abrindo página nova quando acaba
    private static byte[] montarPdf(String titulo, List<String> linhas) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;

            List<String> quebradas = new ArrayList<>();
            for (String linha : linhas) {
                quebradas.addAll(quebrar(semLetraForaDaFonte(linha), normal, largura));
            }

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream tinta = new PDPageContentStream(documento, pagina);
            float y = PDRectangle.A4.getHeight() - MARGEM;
            escrever(tinta, negrito, 15, MARGEM, y, semLetraForaDaFonte(titulo));
            y -= 2 * ENTRELINHA;
            for (String linha : quebradas) {
                if (y < MARGEM) {
                    tinta.close();
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    tinta = new PDPageContentStream(documento, pagina);
                    y = PDRectangle.A4.getHeight() - MARGEM;
                }
                escrever(tinta, normal, CORPO, MARGEM, y, linha);
                y -= ENTRELINHA;
            }
            tinta.close();
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private static void escrever(PDPageContentStream tinta, PDType1Font fonte, float tamanho, float x, float y,
            String texto) throws IOException {
        tinta.beginText();
        tinta.setFont(fonte, tamanho);
        tinta.newLineAtOffset(x, y);
        tinta.showText(texto);
        tinta.endText();
    }

    //A Helvetica embutida no PDF só escreve as letras do Latin-1 (acentos do português inclusive). Outra letra
    //(emoji, travessão, aspas curvas) derrubaria a montagem: vira "?". Quebra de linha dentro do texto vira espaço
    static String semLetraForaDaFonte(String texto) {
        StringBuilder limpo = new StringBuilder();
        texto.codePoints().forEach(letra -> {
            if (letra == '\n' || letra == '\r' || letra == '\t') {
                limpo.append(' ');
            } else if ((letra >= 0x20 && letra < 0x7F) || (letra >= 0xA0 && letra <= 0xFF)) {
                limpo.appendCodePoint(letra);
            } else {
                limpo.append('?');
            }
        });
        return limpo.toString();
    }

    //Quebra pelas palavras, medindo cada pedaço na fonte. Palavra maior que a linha (a assinatura de 64 letras
    //cabe; um texto sem espaço enorme não) é cortada em pedaços
    private static List<String> quebrar(String linha, PDType1Font fonte, float largura) throws IOException {
        List<String> saida = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : linha.split(" ", -1)) {
            String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (medir(tentativa, fonte) <= largura) {
                atual = new StringBuilder(tentativa);
                continue;
            }
            if (!atual.isEmpty()) {
                saida.add(atual.toString());
            }
            atual = new StringBuilder(palavra);
            while (medir(atual.toString(), fonte) > largura) {
                int corte = atual.length() - 1;
                while (corte > 1 && medir(atual.substring(0, corte), fonte) > largura) {
                    corte--;
                }
                saida.add(atual.substring(0, corte));
                atual = new StringBuilder(atual.substring(corte));
            }
        }
        saida.add(atual.toString());
        return saida;
    }

    private static float medir(String texto, PDType1Font fonte) throws IOException {
        return fonte.getStringWidth(texto) / 1000 * CORPO;
    }
}
