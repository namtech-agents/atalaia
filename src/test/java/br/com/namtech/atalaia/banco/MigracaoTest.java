package br.com.namtech.atalaia.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

//Sobe a aplicação com o banco de teste (H2) para o Flyway rodar as migrações
@SpringBootTest
class MigracaoTest {

    //JdbcTemplate: jeito direto de mandar SQL para o banco, sem classe no meio
    @Autowired
    private JdbcTemplate banco;

    @Test
    void migracaoCriaAsTabelasDeEmpresaENotificacao() {
        //Arrumar e agir: a migração já rodou quando a aplicação subiu; conta as linhas de cada tabela
        Integer empresas = banco.queryForObject("select count(*) from empresa", Integer.class);
        Integer notificacoes = banco.queryForObject("select count(*) from notificacao", Integer.class);

        //Conferir: as duas tabelas existem (senão a consulta dava erro em vez de devolver um numero).
        //Nao confere se estao vazias: o banco de teste é o mesmo para todos os testes, e outro pode ter gravado
        assertThat(empresas).isNotNull();
        assertThat(notificacoes).isNotNull();
    }

    //Transactional: desfaz no fim o que este teste grava
    @Test
    @Transactional
    void bancoRecusaDesfechoQueNaoExiste() {
        //Arrumar: um escritório e uma empresa inventados para serem os donos da notificação
        banco.update("insert into escritorio (nome) values ('Escritório do teste de migração')");
        banco.update("""
                insert into empresa (escritorio_id, cnpj, nome)
                select id, '99999009000362', 'Empresa de Teste' from escritorio where nome = 'Escritório do teste de migração'
                """);

        //Agir e conferir: uma notificação resolvida com um desfecho fora dos quatro nao entra,
        //mesmo escrita direto no banco, sem passar pelo Java
        assertThatThrownBy(() -> banco.update("""
                insert into notificacao (empresa_id, arquivo, texto, precisa_de_revisao, resolvida_em, desfecho, observacao)
                select id, X'25504446', 'texto', false, current_timestamp, 'INVENTADO', 'Observação.'
                from empresa where cnpj = '99999009000362'
                """)).isInstanceOf(DataIntegrityViolationException.class)
                //o nome da restrição na mensagem: garante que foi ela que recusou, e nao outra coluna obrigatoria
                .hasMessageContaining("NOTIFICACAO_DESFECHO_VALIDO");
    }
}
