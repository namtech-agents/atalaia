package br.com.namtech.atalaia.analista;

import java.util.List;

import br.com.namtech.atalaia.verificador.DadoExtraido;

//O que o Analista devolve: o tipo do documento, os dados com o trecho de cada um e o aviso de revisao humana.
//Quando precisa de revisao, o motivo diz por que; senao, o motivo vem vazio (null).
//resumoSugerido: o rascunho do aviso ao cliente, ou null se a IA nao mandou um que sirva (ver Analista.resumoQueServe)
public record Analise(TipoDeDocumento tipo, List<DadoExtraido> dados, boolean precisaDeRevisao, String motivo,
        String resumoSugerido) {
}
