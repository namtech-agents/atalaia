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

    //Transactional: desfaz no fim a empresa que este teste grava
    @Test
    @Transactional
    void bancoRecusaDesfechoQueNaoExiste() {
        //Arrumar: uma empresa inventada para ser a dona da notificação
        banco.update("insert into empresa (cnpj, nome) values ('99999009000362', 'Empresa de Teste')");

        //Agir e conferir: uma notificação resolvida com um desfecho fora dos quatro nao entra,
        //mesmo escrita direto no banco, sem passar pelo Java
        assertThatThrownBy(() -> banco.update("""
                insert into notificacao (empresa_id, arquivo, texto, precisa_de_revisao, resolvida_em, desfecho, observacao)
                select id, X'25504446', 'texto', false, current_timestamp, 'INVENTADO', 'Observação.'
                from empresa where cnpj = '99999009000362'
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
