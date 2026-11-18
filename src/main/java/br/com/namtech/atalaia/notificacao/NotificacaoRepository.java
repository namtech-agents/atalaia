package br.com.namtech.atalaia.notificacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
