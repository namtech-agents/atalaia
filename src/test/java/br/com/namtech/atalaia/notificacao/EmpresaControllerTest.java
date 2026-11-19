package br.com.namtech.atalaia.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.NavegadorDeTeste;
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

    private MockMvc navegador;
    private Escritorio escritorio;

    @BeforeEach
    void criaOEscritorioEEntra() {
        escritorio = escritorios.save(new Escritorio("Escritório de Teste"));
        navegador = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
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
}
