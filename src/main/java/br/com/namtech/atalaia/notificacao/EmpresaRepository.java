package br.com.namtech.atalaia.notificacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

//Toda busca leva o escritório de quem entrou: é isso que impede um escritório de ver a empresa de outro.
//Repository (e nao JpaRepository): só existem os metodos escritos aqui, sem findAll nem findById prontos.
//Busca nova tem de ser escrita aqui, e o IsolamentoEntreEscritoriosTest confere se ela leva o escritório.
//O Spring escreve o SQL sozinho a partir do nome do metodo
public interface EmpresaRepository extends Repository<Empresa, Long> {

    Empresa save(Empresa empresa);

    //"As empresas do escritório, em ordem de nome"
    List<Empresa> findAllByEscritorioIdOrderByNome(Long escritorioId);

    //Vazio se a empresa nao existe ou é de outro escritório (quem chama nao fica sabendo qual dos dois)
    Optional<Empresa> findByIdAndEscritorioId(Long id, Long escritorioId);

    boolean existsByEscritorioIdAndCnpj(Long escritorioId, String cnpj);

    //Apaga a empresa só se ela nao tiver nenhum caso, numa operação só: olhar e apagar em dois passos deixaria
    //um envio chegar no meio. Devolve 0 se ela nao existe, é de outro escritório ou tem casos.
    //Os casos sao o historico e a prova do escritório: empresa com caso fica (decisão da dona do projeto, 19/11).
    //Transactional: consulta que muda o banco precisa de uma conversa aberta; Modifying avisa que ela muda
    @Transactional
    @Modifying
    @Query("""
            delete from Empresa e where e.id = ?1 and e.escritorio.id = ?2
              and not exists (select n.id from Notificacao n where n.empresa = e)
            """)
    int excluirSemCasos(Long id, Long escritorioId);
}
