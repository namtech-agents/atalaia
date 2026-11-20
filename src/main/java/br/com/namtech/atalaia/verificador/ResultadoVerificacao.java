package br.com.namtech.atalaia.verificador;

import java.util.List;

//O que o Verificador devolve: o aviso se precisa de revisao humana e o motivo de cada dado rejeitado
public record ResultadoVerificacao(boolean precisaDeRevisao, List<String> motivos) {
}
