package br.com.namtech.atalaia.banco;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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

        //Conferir: as duas tabelas existem (senão a consulta dava erro) e nascem vazias
        assertThat(empresas).isZero();
        assertThat(notificacoes).isZero();
    }
}
