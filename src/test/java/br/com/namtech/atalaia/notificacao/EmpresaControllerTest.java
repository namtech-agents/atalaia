package br.com.namtech.atalaia.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.BancoDeTeste;
import br.com.namtech.atalaia.NavegadorDeTeste;
import br.com.namtech.atalaia.analista.TipoDeDocumento;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;

@SpringBootTest
@Transactional
class EmpresaControllerTest {

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private NotificacaoRepository notificacoes;

    //Para conferir e mexer no banco direto (a data de chegada de um caso antigo)
    @Autowired
    private BancoDeTeste banco;

    @Autowired
    private JdbcTemplate sql;

    private MockMvc navegador;
    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
    }

    @Test
    void contaOsCasosDeCadaClienteNoPeriodoEscolhido() throws Exception {
        //Arrumar: a Padaria com um caso de hoje e um de 60 dias atrás, a Oficina sem caso,
        //e um caso de outro escritório que não pode entrar na conta
        Empresa padaria = empresas.save(new Empresa(escritorio, "99999009003000", "Padaria Trabalhosa"));
        empresas.save(new Empresa(escritorio, "99999009003001", "Oficina Tranquila"));
        casoDa(padaria);
        Notificacao antigo = casoDa(padaria);
        Empresa alheia = empresas.save(new Empresa(escritorios.save(new Escritorio("Outro")), "99999009003002", "Alheia"));
        casoDa(alheia);
        banco.gravar();
        //A data de chegada é posta pelo sistema na hora do envio; o teste volta a do caso antigo direto no banco
        sql.update("update notificacao set recebida_em = ? where id = ?",
                Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)), antigo.getId());

        //Agir e conferir: sem escolher, conta os últimos 30 dias, só o caso de hoje. O mapa entregue à tela
        //tem só a Padaria: o caso do outro escritório não entra (a tela nem mostraria a empresa dele)
        String trintaDias = navegador.perform(get("/empresas"))
                .andExpect(model().attribute("casosNoPeriodo", Map.of(padaria.getId(), 1L)))
                .andReturn().getResponse().getContentAsString();
        assertThat(trintaDias).contains("selected=\"selected\">nos últimos 30 dias");
        assertThat(linhaDa(trintaDias, "Padaria Trabalhosa")).contains("1 caso");
        assertThat(linhaDa(trintaDias, "Oficina Tranquila")).contains("nenhum caso");

        //Agir e conferir: em 90 dias o caso antigo entra
        String noventaDias = navegador.perform(get("/empresas").param("periodo", "90")).andReturn().getResponse()
                .getContentAsString();
        assertThat(linhaDa(noventaDias, "Padaria Trabalhosa")).contains("2 casos");

        //Agir e conferir: um periodo que não está na lista, texto ou numero grande demais (endereço mexido)
        //volta para 30 dias, sem erro
        for (String mexido : new String[] { "7", "abc", "99999999999" }) {
            navegador.perform(get("/empresas").param("periodo", mexido)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("selected=\"selected\">nos últimos 30 dias")));
        }
    }

    private Notificacao casoDa(Empresa empresa) {
        return notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "Texto.",
                TipoDeDocumento.INTIMACAO, List.of(), List.of()));
    }

    //O pedaço da página da linha de uma empresa: do nome até o fim do item da lista
    private static String linhaDa(String pagina, String empresa) {
        int comeco = pagina.indexOf(empresa);
        return pagina.substring(comeco, pagina.indexOf("</li>", comeco));
    }

    @Test
    void escritorioNovoComecaSemEmpresaECadastraAPrimeira() throws Exception {
        //Conferir: a tela convida a cadastrar a primeira
        navegador.perform(get("/empresas"))
                .andExpect(content().string(containsString("Cadastre a primeira empresa")));

        //Agir: cadastra, com o CNPJ digitado com a pontuação
        navegador.perform(post("/empresas").param("nome", "  Padaria de Teste  ").param("cnpj", "99.999.009/0001-15"))
                .andExpect(redirectedUrl("/empresas?cadastrada"));

        //Conferir: ficou guardada no escritório de quem cadastrou, só com as 14 posições, e aparece na lista
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId()))
                .singleElement()
                .satisfies(empresa -> {
                    assertThat(empresa.getNome()).isEqualTo("Padaria de Teste");
                    assertThat(empresa.getCnpj()).isEqualTo("99999009000115");
                });
        //(o aviso de "cadastrada" vem com o caminho para o envio)
        navegador.perform(get("/empresas").param("cadastrada", ""))
                .andExpect(content().string(containsString("Empresa cadastrada.")))
                .andExpect(content().string(containsString("Padaria de Teste")))
                .andExpect(content().string(containsString("99.999.009/0001-15")));
    }

    @Test
    void cnpjIncompletoOuRepetidoERecusadoComMensagem() throws Exception {
        //Arrumar: uma empresa ja cadastrada
        empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));

        //Agir e conferir: CNPJ faltando posição, e depois o mesmo CNPJ de novo
        navegador.perform(post("/empresas").param("nome", "Oficina de Teste").param("cnpj", "99.999.009/0001"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Escreva o CNPJ completo")));
        navegador.perform(post("/empresas").param("nome", "Oficina de Teste").param("cnpj", "99999009000115"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("já está cadastrado")));

        //Conferir: continua só a primeira
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId())).hasSize(1);
    }

    @Test
    void cnpjComDigitoErradoERecusadoComMensagem() throws Exception {
        //Agir e conferir: digito de conferencia errado, e todos os numeros iguais (passa na conta, mas nao existe)
        for (String errado : new String[] {"99.999.009/0001-00", "00.000.000/0000-00"}) {
            navegador.perform(post("/empresas").param("nome", "Oficina de Teste").param("cnpj", errado))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Este CNPJ não existe")));
        }
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId())).isEmpty();
    }

    @Test
    void cnpjValidoConfereOsDoisDigitosInclusiveComLetras() {
        //Agir e conferir: o exemplo de CNPJ com letras publicado pela Receita, um so de numeros, e as variações erradas
        assertThat(Empresa.cnpjValido("12ABC34501DE35")).isTrue();
        assertThat(Empresa.cnpjValido("11222333000181")).isTrue();
        assertThat(Empresa.cnpjValido("12ABC34501DE36")).isFalse();
        assertThat(Empresa.cnpjValido("11222333000180")).isFalse();
        assertThat(Empresa.cnpjValido("11111111111111")).isFalse();
    }

    @Test
    void mesmoCnpjPodeSerClienteDeDoisEscritorios() throws Exception {
        //Arrumar: outro escritório ja tem uma empresa com este CNPJ
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        empresas.save(new Empresa(outro, "99999009000115", "Padaria de Teste"));

        //Agir e conferir: o escritório de quem entrou cadastra a mesma empresa sem erro
        navegador.perform(post("/empresas").param("nome", "Padaria de Teste").param("cnpj", "99999009000115"))
                .andExpect(redirectedUrl("/empresas?cadastrada"));
        assertThat(empresas.findAllByEscritorioIdOrderByNome(escritorio.getId())).hasSize(1);
    }

    @Test
    void corrigeONomeEOCnpjDaEmpresa() throws Exception {
        //Arrumar: uma empresa cadastrada com o nome errado
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Tetse"));

        //Agir e conferir: a tela de editar abre com os dados atuais
        navegador.perform(get("/empresas/" + empresa.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Padaria de Tetse")));

        //Agir: corrige só o nome (o mesmo CNPJ não conta como repetido) e depois o CNPJ
        navegador.perform(post("/empresas/" + empresa.getId()).param("nome", "Padaria de Teste").param("cnpj", "99999009000115"))
                .andExpect(redirectedUrl("/empresas?alterada"));
        navegador.perform(post("/empresas/" + empresa.getId()).param("nome", "Padaria de Teste").param("cnpj", "11.222.333/0001-81"))
                .andExpect(redirectedUrl("/empresas?alterada"));

        //Conferir: ficou guardado
        assertThat(empresas.findByIdAndEscritorioId(empresa.getId(), escritorio.getId())).hasValueSatisfying(salva -> {
            assertThat(salva.getNome()).isEqualTo("Padaria de Teste");
            assertThat(salva.getCnpj()).isEqualTo("11222333000181");
        });
        navegador.perform(get("/empresas").param("alterada", ""))
                .andExpect(content().string(containsString("Empresa alterada.")));
    }

    @Test
    void correcaoComCnpjDeOutraEmpresaDoEscritorioERecusada() throws Exception {
        //Arrumar: duas empresas do escritório
        empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        Empresa oficina = empresas.save(new Empresa(escritorio, "11222333000181", "Oficina de Teste"));

        //Agir e conferir: a oficina não pode ficar com o CNPJ da padaria
        navegador.perform(post("/empresas/" + oficina.getId()).param("nome", "Oficina de Teste").param("cnpj", "99999009000115"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("já está cadastrado")))
                //o título continua com o nome guardado, e o campo volta com o que a pessoa digitou
                .andExpect(content().string(containsString("<h1>Oficina de Teste</h1>")))
                .andExpect(content().string(containsString("value=\"99999009000115\"")));
        assertThat(empresas.findByIdAndEscritorioId(oficina.getId(), escritorio.getId()))
                .hasValueSatisfying(salva -> assertThat(salva.getCnpj()).isEqualTo("11222333000181"));
    }

    @Test
    void empresaSemCasosPodeSerExcluida() throws Exception {
        //Arrumar: uma empresa cadastrada por engano, sem nenhum caso
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));

        //Agir e conferir: some da lista
        navegador.perform(post("/empresas/" + empresa.getId() + "/excluir"))
                .andExpect(redirectedUrl("/empresas?excluida"));
        assertThat(empresas.findByIdAndEscritorioId(empresa.getId(), escritorio.getId())).isEmpty();

        //Conferir: clique duplo (o segundo pedido já não acha a empresa) responde 404, sem página de erro do banco
        navegador.perform(post("/empresas/" + empresa.getId() + "/excluir")).andExpect(status().isNotFound());
    }

    @Test
    void empresaComCasosNaoPodeSerExcluida() throws Exception {
        //Arrumar: uma empresa com um caso registrado
        Empresa empresa = empresas.save(new Empresa(escritorio, "99999009000115", "Padaria de Teste"));
        notificacoes.save(new Notificacao(empresa, "%PDF-teste".getBytes(), "texto", TipoDeDocumento.COBRANCA,
                List.of(), List.of()));

        //Conferir: a tela de editar não oferece excluir e explica o porquê
        navegador.perform(get("/empresas/" + empresa.getId()))
                .andExpect(content().string(containsString("tem 1 caso")))
                .andExpect(content().string(not(containsString("Excluir Padaria"))));

        //Agir e conferir: com a tela aberta de antes, o pedido chega mesmo assim; a empresa fica (409: conflita
        //com os casos dela) e a tela explica o porquê
        navegador.perform(post("/empresas/" + empresa.getId() + "/excluir"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("tem 1 caso")));
        assertThat(empresas.findByIdAndEscritorioId(empresa.getId(), escritorio.getId())).isPresent();
    }

    @Test
    void empresaDeOutroEscritorioNaoAbreNaoMudaENaoSai() throws Exception {
        //Arrumar: uma empresa de outro escritório
        Escritorio outro = escritorios.save(new Escritorio("Outro Escritório"));
        Empresa alheia = empresas.save(new Empresa(outro, "99999009000115", "Empresa Secreta"));

        //Agir e conferir: abrir, corrigir e excluir respondem como se ela não existisse
        navegador.perform(get("/empresas/" + alheia.getId())).andExpect(status().isNotFound());
        navegador.perform(post("/empresas/" + alheia.getId()).param("nome", "Trocada").param("cnpj", "11222333000181"))
                .andExpect(status().isNotFound());
        navegador.perform(post("/empresas/" + alheia.getId() + "/excluir")).andExpect(status().isNotFound());

        //Conferir: continua igual
        assertThat(empresas.findByIdAndEscritorioId(alheia.getId(), outro.getId()))
                .hasValueSatisfying(salva -> assertThat(salva.getNome()).isEqualTo("Empresa Secreta"));
    }
}
