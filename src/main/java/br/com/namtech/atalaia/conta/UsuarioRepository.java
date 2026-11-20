package br.com.namtech.atalaia.conta;

import java.util.Optional;

import org.springframework.data.repository.Repository;

//Repository (e nao JpaRepository): a conta é buscada só pelo e-mail de quem entra; sem findAll, nenhuma tela
//consegue listar as pessoas de todos os escritórios
public interface UsuarioRepository extends Repository<Usuario, Long> {

    Usuario save(Usuario usuario);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
