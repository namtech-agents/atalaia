package br.com.namtech.atalaia.notificacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

//Repository (e nao JpaRepository): só existem os metodos escritos aqui, todos com o escritório, mais o save.
//O compilador barra quem tentar um findAll ou findById sem filtro (ver EventoDoCasoRepository e o
//IsolamentoEntreEscritoriosTest, que confere todos os repositórios)
//O Spring escreve o SQL sozinho a partir do nome do metodo
public interface EmpresaRepository extends Repository<Empresa, Long> {

    Empresa save(Empresa empresa);


    //Toda busca leva o escritório de quem entrou: é isso que impede um escritório de ver a empresa de outro

    //"As empresas do escritório, em ordem de nome"
    List<Empresa> findAllByEscritorioIdOrderByNome(Long escritorioId);

    //Vazio se a empresa nao existe ou é de outro escritório (quem chama nao fica sabendo qual dos dois)
    Optional<Empresa> findByIdAndEscritorioId(Long id, Long escritorioId);

    boolean existsByEscritorioId(Long escritorioId);

    boolean existsByEscritorioIdAndCnpj(Long escritorioId, String cnpj);
}
