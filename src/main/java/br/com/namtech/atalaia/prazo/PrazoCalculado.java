package br.com.namtech.atalaia.prazo;

import java.time.LocalDate;
import java.util.List;

//O que a calculadora devolve: a data limite, a conta passo a passo e o aviso se precisa de revisao humana.
//Quando precisa de revisao, a data limite vem vazia (null): prazo sem conferencia nao aparece para ninguem.
public record PrazoCalculado(LocalDate dataLimite, List<String> passos, boolean precisaDeRevisao) {
}
