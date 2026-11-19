package br.com.namtech.atalaia.historico;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

//Repository (e nao JpaRepository): só existem os dois metodos escritos aqui. Nao ha findAll nem findById sem o
//escritório, nem delete: quem usar este repositorio nao consegue, nem por engano, ver o historico de outro
//escritório ou apagar um evento. O compilador barra antes de rodar
interface EventoDoCasoRepository extends Repository<EventoDoCaso, Long> {

    EventoDoCaso save(EventoDoCaso evento);

    //Os eventos de um caso, do mais antigo para o mais novo. O id desempata dois eventos no mesmo instante
    @Query("""
            select e from EventoDoCaso e
            where e.notificacao.id = ?1 and e.notificacao.empresa.escritorio.id = ?2
            order by e.quando, e.id
            """)
    List<EventoDoCaso> doCaso(Long notificacaoId, Long escritorioId);
}
