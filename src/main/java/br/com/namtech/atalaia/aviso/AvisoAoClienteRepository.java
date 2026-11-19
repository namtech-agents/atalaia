package br.com.namtech.atalaia.aviso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.namtech.atalaia.notificacao.Notificacao;
import jakarta.persistence.LockModeType;

//Repository (e nao JpaRepository): só existem os metodos escritos aqui, sem findAll nem findById prontos.
//As duas buscas por token sao da pagina do cliente, que nao tem login: o token sorteado é a chave
//Busca nova tem de ser escrita aqui, e o IsolamentoEntreEscritoriosTest confere se ela leva o escritório
public interface AvisoAoClienteRepository extends Repository<AvisoAoCliente, Long> {

    AvisoAoCliente save(AvisoAoCliente avisoAoCliente);

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

    //Para a lista da tela inicial: o aviso que vale (o mais novo) de cada caso aberto do escritório, numa busca só
    @Query("""
            select a from AvisoAoCliente a
            where a.notificacao.empresa.escritorio.id = ?1 and a.notificacao.resolvidaEm is null
              and a.criadoEm = (select max(outro.criadoEm) from AvisoAoCliente outro where outro.notificacao = a.notificacao)
            """)
    List<AvisoAoCliente> dosCasosAbertos(Long escritorioId);

    //O caso do aviso, sem trancar nada: a confirmação tranca primeiro o caso e só depois o aviso,
    //na mesma ordem do escritório (caso, depois avisos). Ordem trocada entre dois pedidos dá deadlock no Postgres
    @Query("select a.notificacao from AvisoAoCliente a where a.token = ?1")
    Optional<Notificacao> casoDoToken(String token);

    //A pagina do cliente nao tem login: quem tem o token ve só aquele aviso, e nada mais
    Optional<AvisoAoCliente> findByToken(String token);

    //Busca para confirmar, trancando a linha: dois cliques juntos no botão nao gravam dois registros de ciência
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AvisoAoCliente a where a.token = ?1")
    Optional<AvisoAoCliente> buscarParaConfirmar(String token);
}
