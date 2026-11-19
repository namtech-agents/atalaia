package br.com.namtech.atalaia.aviso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface AvisoAoClienteRepository extends JpaRepository<AvisoAoCliente, Long> {

    //Os avisos de uma notificação, o mais novo primeiro. Leva o escritório de quem entrou:
    //um escritório nao ve o aviso de outro
    @Query("""
            select a from AvisoAoCliente a
            where a.notificacao.id = ?1 and a.notificacao.empresa.escritorio.id = ?2
            order by a.criadoEm desc, a.id desc
            """)
    List<AvisoAoCliente> doCaso(Long notificacaoId, Long escritorioId);

    //A mesma busca, trancando as linhas, para o aviso novo que vai substituir os anteriores. Sem a tranca,
    //uma confirmação do cliente gravada no mesmo instante seria apagada: quem chega depois espera e le o que ficou
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AvisoAoCliente a
            where a.notificacao.id = ?1 and a.notificacao.empresa.escritorio.id = ?2
            order by a.criadoEm desc, a.id desc
            """)
    List<AvisoAoCliente> doCasoParaSubstituir(Long notificacaoId, Long escritorioId);

    //A pagina do cliente nao tem login: quem tem o token ve só aquele aviso, e nada mais
    Optional<AvisoAoCliente> findByToken(String token);

    //Busca para confirmar, trancando a linha: dois cliques juntos no botão nao gravam dois registros de ciência
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AvisoAoCliente a where a.token = ?1")
    Optional<AvisoAoCliente> buscarParaConfirmar(String token);
}
