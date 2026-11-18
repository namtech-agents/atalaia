package br.com.namtech.atalaia.notificacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    //A lista da tela inicial, da mais nova para a mais antiga. O "select new" monta cada linha só com as
    //colunas pedidas: o PDF e o texto ficam no banco
    @Query("""
            select new br.com.namtech.atalaia.notificacao.NotificacaoNaLista(
                n.id, e.nome, n.tipo, n.precisaDeRevisao, n.recebidaEm, n.desfecho)
            from Notificacao n join n.empresa e
            order by n.recebidaEm desc, n.id desc
            """)
    List<NotificacaoNaLista> listar();

    //Busca a notificação para alterar, trancando a linha dela no banco ate a mudança ser gravada.
    //Dois pedidos de resolver ao mesmo tempo: o segundo espera o primeiro terminar e ja encontra o caso resolvido.
    //Só funciona dentro de um metodo Transactional (a tranca dura enquanto a conversa com o banco estiver aberta)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notificacao n where n.id = ?1")
    Optional<Notificacao> buscarParaAlterar(Long id);
}
