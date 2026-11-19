package br.com.namtech.atalaia.notificacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

//Toda busca leva o escritório de quem entrou: é isso que impede um escritório de ver a notificação de outro.
//Repository (e nao JpaRepository): só existem os metodos escritos aqui, sem findAll nem findById prontos.
//Busca nova tem de ser escrita aqui, e o IsolamentoEntreEscritoriosTest confere se ela leva o escritório
public interface NotificacaoRepository extends Repository<Notificacao, Long> {

    Notificacao save(Notificacao notificacao);

    //A lista da tela inicial, da mais nova para a mais antiga. O "select new" monta cada linha só com as
    //colunas pedidas: o PDF e o texto ficam no banco
    @Query("""
            select new br.com.namtech.atalaia.notificacao.NotificacaoNaLista(
                n.id, e.nome, n.tipo, n.precisaDeRevisao, n.recebidaEm, n.desfecho, n.resolvidaEm, n.dataLimite)
            from Notificacao n join n.empresa e
            where e.escritorio.id = ?1
            order by n.recebidaEm desc, n.id desc
            """)
    List<NotificacaoNaLista> listar(Long escritorioId);

    //Quantos casos a empresa tem: a tela de editar explica por que empresa com caso nao sai
    @Query("select count(n) from Notificacao n where n.empresa.id = ?1 and n.empresa.escritorio.id = ?2")
    long casosDaEmpresa(Long empresaId, Long escritorioId);

    //Vazio se a notificação nao existe ou é de outro escritório (quem chama nao fica sabendo qual dos dois)
    @Query("select n from Notificacao n where n.id = ?1 and n.empresa.escritorio.id = ?2")
    Optional<Notificacao> buscar(Long id, Long escritorioId);

    //Busca a notificação para alterar, trancando a linha dela no banco ate a mudança ser gravada.
    //Dois pedidos de resolver ao mesmo tempo: o segundo espera o primeiro terminar e ja encontra o caso resolvido.
    //Só funciona dentro de um metodo Transactional (a tranca dura enquanto a conversa com o banco estiver aberta)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notificacao n where n.id = ?1 and n.empresa.escritorio.id = ?2")
    Optional<Notificacao> buscarParaAlterar(Long id, Long escritorioId);
}
