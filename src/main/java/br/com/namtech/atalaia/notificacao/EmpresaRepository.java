package br.com.namtech.atalaia.notificacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

//Repository: o Spring escreve o SQL sozinho a partir do nome do metodo
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    //"Todas as empresas, em ordem de nome", para a lista da tela de envio
    List<Empresa> findAllByOrderByNome();
}
