package br.com.namtech.atalaia.prazo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

//Toda busca leva o escritório de quem entrou: um escritório nao ve nem usa a regra de outro
//Repository (e nao JpaRepository): só existem os metodos escritos aqui, todos com o escritório, mais o save.
//O compilador barra quem tentar um findAll ou findById sem filtro (ver EventoDoCasoRepository e o
//IsolamentoEntreEscritoriosTest, que confere todos os repositórios)
public interface RegraCadastradaRepository extends Repository<RegraCadastrada, Long> {

    RegraCadastrada save(RegraCadastrada regraCadastrada);


    List<RegraCadastrada> findAllByEscritorioIdOrderByNome(Long escritorioId);

    //Vazio se a regra nao existe ou é de outro escritório (quem chama nao fica sabendo qual dos dois)
    Optional<RegraCadastrada> findByIdAndEscritorioId(Long id, Long escritorioId);

    boolean existsByEscritorioIdAndNome(Long escritorioId, String nome);

    //Apaga direto no banco e devolve quantas linhas saíram: 0 se a regra nao existe, é de outro escritório
    //ou ja foi apagada por um clique anterior. Modifying: avisa que esta consulta muda o banco
    @Modifying
    @Query("delete from RegraCadastrada r where r.id = ?1 and r.escritorio.id = ?2")
    int excluirDoEscritorio(Long id, Long escritorioId);
}
