package br.com.namtech.atalaia.prazo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

//Toda busca leva o escritório de quem entrou: um escritório nao ve nem usa a regra de outro
public interface RegraCadastradaRepository extends JpaRepository<RegraCadastrada, Long> {

    List<RegraCadastrada> findAllByEscritorioIdOrderByNome(Long escritorioId);

    //Vazio se a regra nao existe ou é de outro escritório (quem chama nao fica sabendo qual dos dois)
    Optional<RegraCadastrada> findByIdAndEscritorioId(Long id, Long escritorioId);

    boolean existsByEscritorioIdAndNome(Long escritorioId, String nome);
}
