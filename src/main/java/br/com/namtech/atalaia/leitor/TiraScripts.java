package br.com.namtech.atalaia.leitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;

//Tira de um PDF o que age sozinho no leitor de quem abre: scripts (JavaScript), abrir programa (Launch),
//mandar dados para fora (SubmitForm), abrir arquivo da rede (GoToR para \\servidor\...) e o que mais existir.
//Lista do PERMITIDO, não do proibido: fica só "ir para uma página" e link de site (http, https) ou de e-mail.
//Qualquer outra ação sai, inclusive as que ainda não conhecemos (revisão do Hefesto, PR #184).
//Em vez de procurar em cada lugar conhecido, percorre TODOS os objetos do arquivo: o lugar que esquecermos
//é justamente o que quem ataca vai usar.
//O que se perde num PDF legítimo: formulário só em XFA vira página vazia e campo calculado por script deixa de
//calcular. Só na cópia que a tela mostra; o original fica guardado
final class TiraScripts {

    //Chaves que só existem para guardar scripts: AA = ações ao abrir/fechar a página, ao passar o mouse,
    //ao digitar num campo...; JavaScript = o índice de scripts do documento; XFA = formulário com scripts dentro;
    //OnInstantiate e Scripts = scripts de anotação 3D e de mídia
    private static final Set<COSName> CHAVES_DE_SCRIPT = Set.of(COSName.AA, COSName.JAVA_SCRIPT, COSName.XFA,
            COSName.getPDFName("OnInstantiate"), COSName.getPDFName("Scripts"));

    //Onde uma ação mora: A (no link, no marcador, no campo), OpenAction (ao abrir o arquivo) e Next (a ação
    //seguinte, sozinha ou numa lista). /S também aparece fora de ação (transparência, borda): por isso a regra
    //das ações vale só nestas chaves
    private static final Set<COSName> CHAVES_DE_ACAO = Set.of(COSName.A, COSName.OPEN_ACTION, COSName.NEXT);

    //GoTo: ir para uma página do próprio PDF (o PDFBox não tem a constante pronta)
    private static final COSName IR_PARA_PAGINA = COSName.getPDFName("GoTo");

    private static final Set<String> ENDERECOS_PERMITIDOS = Set.of("http:", "https:", "mailto:");

    private TiraScripts() {
    }

    //Devolve true se tirou alguma coisa. Muda o documento aberto; quem chama decide se grava uma copia
    static boolean tirar(COSDocument documento) {
        boolean tirou = false;
        //Cada objeto "indireto" (com número próprio no arquivo) é visitado uma vez aqui
        for (COSObjectKey chave : List.copyOf(documento.getXrefTable().keySet())) {
            COSObject objeto = documento.getObjectFromPool(chave);
            tirou |= limpar(objeto.getObject());
        }
        return tirou;
    }

    //Limpa um objeto e os que estão escritos dentro dele. Não segue as referências para outros objetos
    //indiretos: esses já passam pelo laço de tirar(), e segui-las poderia andar em círculo
    private static boolean limpar(COSBase objeto) {
        boolean tirou = false;
        if (objeto instanceof COSDictionary dicionario) {
            for (Map.Entry<COSName, COSBase> item : new ArrayList<>(dicionario.entrySet())) {
                COSName chave = item.getKey();
                COSBase valor = item.getValue();
                if (CHAVES_DE_SCRIPT.contains(chave)) {
                    dicionario.removeItem(chave);
                    tirou = true;
                } else if (CHAVES_DE_ACAO.contains(chave) && abrir(valor) instanceof COSDictionary acao) {
                    if (!permitida(acao)) {
                        dicionario.removeItem(chave);
                        tirou = true;
                    }
                } else if (chave.equals(COSName.NEXT) && abrir(valor) instanceof COSArray seguintes) {
                    tirou |= tirarDaLista(seguintes);
                } else if (!(valor instanceof COSObject)) {
                    tirou |= limpar(valor);
                }
            }
        } else if (objeto instanceof COSArray lista) {
            for (COSBase valor : lista) {
                if (!(valor instanceof COSObject)) {
                    tirou |= limpar(valor);
                }
            }
        }
        return tirou;
    }

    //Next com uma lista de ações: sai cada uma que não é permitida.
    //De trás para frente: tirar um item não muda a posição dos que ainda faltam
    private static boolean tirarDaLista(COSArray acoes) {
        boolean tirou = false;
        for (int posicao = acoes.size() - 1; posicao >= 0; posicao--) {
            if (abrir(acoes.get(posicao)) instanceof COSDictionary acao && !permitida(acao)) {
                acoes.remove(posicao);
                tirou = true;
            }
        }
        return tirou;
    }

    //Uma ação é um dicionário com /S dizendo o tipo. Sem /S nenhum leitor de PDF executa nada: fica
    private static boolean permitida(COSDictionary acao) {
        COSName tipo = acao.getCOSName(COSName.S);
        if (tipo == null || tipo.equals(IR_PARA_PAGINA)) {
            return true;
        }
        if (tipo.equals(COSName.URI)) {
            //Sem diferenciar maiúscula e sem espaço na frente: "  JavaScript:..." também é javascript:
            String endereco = acao.getString(COSName.URI, "").strip().toLowerCase(Locale.ROOT);
            return ENDERECOS_PERMITIDOS.stream().anyMatch(endereco::startsWith);
        }
        return false;
    }

    //Se o valor é uma referência ("veja o objeto 12"), devolve o objeto 12; senão, o próprio valor
    private static COSBase abrir(COSBase valor) {
        return valor instanceof COSObject referencia ? referencia.getObject() : valor;
    }
}
