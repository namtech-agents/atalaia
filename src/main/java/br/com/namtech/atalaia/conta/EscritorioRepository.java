package br.com.namtech.atalaia.conta;

import org.springframework.data.repository.Repository;

//Repository (e nao JpaRepository): o sistema só grava o escritório (no cadastro) e liga dados ao escritório de quem
//entrou. Sem findAll nem findById, nenhuma tela consegue listar ou abrir o escritório de outra pessoa
public interface EscritorioRepository extends Repository<Escritorio, Long> {

    Escritorio save(Escritorio escritorio);

    //Uma referência ao escritório de quem entrou, sem ir ao banco: serve para ligar a empresa ou a regra nova a ele.
    //Quem chama passa sempre o escritorioId da sessão (UsuarioLogado), nunca um número vindo do formulário
    Escritorio getReferenceById(Long id);

}
