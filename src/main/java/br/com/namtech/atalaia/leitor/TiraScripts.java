package br.com.namtech.atalaia.leitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;

//Tira de um PDF o que roda sozinho no leitor de quem abre: scripts (JavaScript), abrir programa (Launch),
//mandar dados para fora (SubmitForm) e parecidos. O texto, as imagens e os links de site ficam.
//Em vez de procurar em cada lugar conhecido (ao abrir, na página, no link, no formulário...), percorre
//TODOS os objetos do arquivo: o lugar que esquecermos é justamente o que quem ataca vai usar
final class TiraScripts {

    //Ações que fazem algo sozinhas ou falam com o mundo de fora. Ir para uma página, ou abrir um site
    //(o navegador pergunta e abre em outra aba), continuam
    private static final Set<COSName> ACOES_PERIGOSAS = Set.of(COSName.JAVA_SCRIPT, COSName.getPDFName("Launch"),
            COSName.getPDFName("SubmitForm"), COSName.getPDFName("ImportData"), COSName.getPDFName("Rendition"),
            COSName.getPDFName("RichMediaExecute"), COSName.getPDFName("GoToE"));

    //Chaves que só existem para guardar scripts:
    //AA = ações ao abrir/fechar a página, ao passar o mouse, ao digitar num campo...;
    //JavaScript = o índice de scripts do documento; XFA = formulário que traz scripts dentro
    private static final Set<COSName> CHAVES_DE_SCRIPT = Set.of(COSName.AA, COSName.JAVA_SCRIPT, COSName.XFA);

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
                //OpenAction com uma lista é só "abrir nesta página"; com um dicionário é uma ação, e sai
                boolean acaoAoAbrir = chave.equals(COSName.OPEN_ACTION) && abrir(valor) instanceof COSDictionary;
                if (CHAVES_DE_SCRIPT.contains(chave) || acaoAoAbrir || perigosa(valor)) {
                    dicionario.removeItem(chave);
                    tirou = true;
                } else if (!(valor instanceof COSObject)) {
                    tirou |= limpar(valor);
                }
            }
        } else if (objeto instanceof COSArray lista) {
            //De trás para frente: tirar um item não muda a posição dos que ainda faltam
            for (int posicao = lista.size() - 1; posicao >= 0; posicao--) {
                COSBase valor = lista.get(posicao);
                if (perigosa(valor)) {
                    lista.remove(posicao);
                    tirou = true;
                } else if (!(valor instanceof COSObject)) {
                    tirou |= limpar(valor);
                }
            }
        }
        return tirou;
    }

    //Uma ação é um dicionário com /S dizendo o tipo. Dicionário sem /S nao é ação (e o Set.of nao aceita
    //perguntar por null: daria NullPointerException)
    private static boolean perigosa(COSBase valor) {
        if (!(abrir(valor) instanceof COSDictionary acao)) {
            return false;
        }
        COSName tipo = acao.getCOSName(COSName.S);
        return tipo != null && ACOES_PERIGOSAS.contains(tipo);
    }

    //Se o valor é uma referência ("veja o objeto 12"), devolve o objeto 12; senão, o próprio valor
    private static COSBase abrir(COSBase valor) {
        return valor instanceof COSObject referencia ? referencia.getObject() : valor;
    }
}
