package br.com.namtech.atalaia.notificacao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
                n.id, e.id, e.nome, n.tipo, n.degrau, n.precisaDeRevisao, n.recebidaEm, n.desfecho, n.resolvidaEm, n.dataLimite)
            from Notificacao n join n.empresa e
            where e.escritorio.id = ?1
            order by n.recebidaEm desc, n.id desc
            """)
    List<NotificacaoNaLista> listar(Long escritorioId);

    //Os simulados do escritório, do mais novo para o mais antigo (tela do simulado). São poucos e o PDF é pequeno
    @Query("""
            select n from Notificacao n
            where n.empresa.escritorio.id = ?1 and n.simulado = true
            order by n.recebidaEm desc, n.id desc
            """)
    List<Notificacao> simulados(Long escritorioId);

    //Marca a primeira abertura do simulado, numa operação só no banco: dois que abrem juntos não marcam duas vezes,
    //porque só um encontra aberta_em vazio. Devolve 1 se marcou, 0 se não era simulado, já estava marcado ou é de
    //outro escritório. Caso comum não é tocado (nem trancado) ao ser aberto.
    //A empresa vai por subconsulta: o update do JPA não aceita "n.empresa.escritorio" direto
    @Modifying
    @Query("""
            update Notificacao n set n.abertaEm = ?3
            where n.id = ?1 and n.simulado = true and n.abertaEm is null
              and n.empresa.id in (select e.id from Empresa e where e.escritorio.id = ?2)
            """)
    int marcarAbertura(Long id, Long escritorioId, Instant quando);

    //Quantos casos cada empresa do escritório recebeu desde a data dada (tela de empresas, para rever a cobrança).
    //Empresa sem nenhum caso no periodo nao vem na lista: a tela mostra zero para ela
    @Query("""
            select new br.com.namtech.atalaia.notificacao.CasosDaEmpresa(n.empresa.id, count(n))
            from Notificacao n
            where n.empresa.escritorio.id = ?1 and n.recebidaEm >= ?2 and n.simulado = false
            group by n.empresa.id
            """)
    List<CasosDaEmpresa> contarPorEmpresa(Long escritorioId, Instant desde);

    //A faxina de sexta: os casos abertos em que ninguem mexe desde antes da data dada. O "movimento" é o evento
    //mais novo do historico do caso. Todo caso tem ao menos o "Enviou o PDF" (o envio grava junto, e a V11 preencheu
    //os antigos); o coalesce com a chegada é só uma defesa barata para um caso sem evento, que nao deveria existir.
    //"left join ... on": junta os eventos sem precisar de uma lista de eventos dentro da Notificacao
    @Query("""
            select new br.com.namtech.atalaia.notificacao.CasoParado(
                n.id, emp.nome, n.tipo, n.dataLimite, coalesce(max(e.quando), n.recebidaEm))
            from Notificacao n join n.empresa emp left join EventoDoCaso e on e.notificacao = n
            where emp.escritorio.id = ?1 and n.resolvidaEm is null
            group by n.id, emp.nome, n.tipo, n.dataLimite, n.recebidaEm
            having coalesce(max(e.quando), n.recebidaEm) < ?2
            """)
    List<CasoParado> parados(Long escritorioId, Instant semMovimentoDesde);

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
