package br.com.namtech.atalaia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.namtech.atalaia.analista.ModeloDeIA;
import br.com.namtech.atalaia.conta.Escritorio;
import br.com.namtech.atalaia.conta.EscritorioRepository;
import br.com.namtech.atalaia.notificacao.Empresa;
import br.com.namtech.atalaia.notificacao.Notificacao;
import br.com.namtech.atalaia.prazo.RegraCadastrada;

//O objetivo do Marco 3 num teste só: o caminho inteiro que o Atalaia promete, feito pela tela, como uma pessoa faria.
//PDF → dados conferidos → prazo por código → cliente confirma com registro → caso resolvido.
//Cada passo tem os seus testes detalhados em outras classes; este prova que os passos se encaixam.
//Lido de cima para baixo, também serve de roteiro do sistema: cada bloco é uma tela.
//Só a IA é de mentira (sem internet e sem gastar a chave); o resto é a aplicação de verdade, com o banco de teste.
//Limite conhecido: com o @Transactional, o caminho todo roda numa conversa só com o banco, desfeita no fim.
//Por isso este teste não pega erro de relação LAZY fora de transação nem confere a gravação de verdade (commit)
//entre um pedido e outro; isso fica com os testes sem transação, como o ResolverAoMesmoTempoTest
@SpringBootTest
@Transactional
class CaminhoInteiroTest {

    //O token do link do cliente: letras de A a Z, a a z, 0 a 9, - e _ (Base64 de endereço). Sem tamanho fixo:
    //se o token mudar de tamanho, o teste continua achando o link inteiro
    private static final Pattern LINK_DO_CLIENTE = Pattern.compile("/c/([A-Za-z0-9_-]+)");

    @Autowired
    private WebApplicationContext aplicacao;

    @Autowired
    private EscritorioRepository escritorios;

    //Para achar o que cada tela acabou de criar (a empresa, a regra, o caso)
    @Autowired
    private BancoDeTeste banco;

    @MockitoBean
    private ModeloDeIA ia;

    @Test
    void doPdfAteOCasoResolvido() throws Exception {
        //Arrumar: um escritório com uma pessoa já dentro, e o navegador do cliente, que não tem conta.
        //O cadastro e a entrada têm testes próprios (ContaControllerTest); aqui o caminho começa com a sessão aberta
        Escritorio escritorio = escritorios.save(new Escritorio("Escritório do Caminho"));
        MockMvc escritorioLogado = NavegadorDeTeste.logado(aplicacao, NavegadorDeTeste.pessoaDo(escritorio.getId()));
        MockMvc cliente = MockMvcBuilders.webAppContextSetup(aplicacao).apply(springSecurity()).build();
        //Ciência hoje (o Atalaia não aceita ciência nem conferência no futuro): a data limite cai no futuro,
        //e o link do cliente pode ser gerado
        LocalDate hoje = LocalDate.now(Notificacao.TERESINA);
        //A IA de mentira lê o PDF de exemplo: um dado que está mesmo no texto, e o resumo para o cliente
        when(ia.responder(anyString())).thenReturn("""
                {"tipo": "INTIMACAO", "confianca": "alta", "dados": [
                  {"campo": "prazo", "valor": "30 dias", "trecho": "Prazo: 30 dias a contar da ciência"}],
                 "resumo": "A Receita pediu os livros da empresa. Separe os livros e avise o escritório."}
                """);

        //1. Cadastro: a empresa cliente e a regra de prazo do escritório (cada escritório cadastra a sua)
        escritorioLogado.perform(post("/empresas").param("nome", "Padaria do Caminho").param("cnpj", "99.999.009/0001-15"))
                .andExpect(redirectedUrl("/empresas?cadastrada"));
        Empresa empresa = banco.ultima(Empresa.class);
        escritorioLogado.perform(post("/regras").param("nome", "Resposta à intimação").param("dias", "15")
                .param("contagem", "DIAS_CORRIDOS").param("suspendeNoRecesso", "") //vazio = a caixa desmarcada
                .param("artigo", "Norma de Teste, art. 1").param("conferidaEm", hoje.toString()))
                .andExpect(redirectedUrl("/regras?cadastrada"));
        RegraCadastrada regra = banco.ultima(RegraCadastrada.class);

        //2. Envio do PDF: o Leitor tira o texto, a IA classifica e extrai, o Verificador confere com o texto
        String enderecoDoCaso = escritorioLogado.perform(multipart("/enviar").file(pdfDeExemplo())
                .param("empresaId", empresa.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Notificacao caso = banco.ultima(Notificacao.class);
        assertThat(enderecoDoCaso).isEqualTo("/notificacoes/" + caso.getId());
        //A tela do caso mostra a leitura conferida, sem nada para revisar
        escritorioLogado.perform(get(enderecoDoCaso))
                .andExpect(content().string(containsString("Lido e conferido")))
                .andExpect(content().string(containsString("30 dias")));
        //Na tela inicial, o caso está com o escritório: falta calcular o prazo
        confereATelaInicial(escritorioLogado, 1, 0, 0, "Calcular o prazo");

        //3. Prazo por código: a pessoa escolhe a regra e o dia da ciência; quem conta os dias é a Calculadora
        escritorioLogado.perform(post(enderecoDoCaso + "/prazo").param("regraId", regra.getId().toString())
                .param("ciencia", hoje.toString()))
                .andExpect(redirectedUrl(enderecoDoCaso + "#titulo-prazo"));
        LocalDate dataLimite = caso.getDataLimite();
        //15 dias corridos depois da ciência, ou alguns a mais se o último dia cair sem expediente (fim de semana
        //com feriado colado empurra uns 4 dias no máximo). A conta exata fica com o CalculadoraDePrazoTest
        assertThat(dataLimite).isBetween(hoje.plusDays(15), hoje.plusDays(15 + 7));
        String dataNaTela = Datas.DIA.format(dataLimite);
        escritorioLogado.perform(get(enderecoDoCaso))
                .andExpect(content().string(containsString("Data limite: " + dataNaTela + ".")))
                .andExpect(content().string(containsString("Norma de Teste, art. 1")))
                //Com o prazo na tela, abre o aviso ao cliente, com o rascunho do resumo que a IA escreveu
                .andExpect(content().string(containsString("A Receita pediu os livros da empresa.")));
        confereATelaInicial(escritorioLogado, 1, 0, 0, "Avisar o cliente");

        //4. Aviso ao cliente: o escritório gera o link com o resumo e os documentos para separar
        escritorioLogado.perform(post(enderecoDoCaso + "/aviso")
                .param("resumo", "Chegou uma intimação da Receita. Separe os livros.")
                .param("documentos", "Livro caixa de 2025"))
                .andExpect(redirectedUrl(enderecoDoCaso + "#titulo-aviso"));
        //O link sai da tela do escritório, do jeito que a pessoa copiaria para mandar ao cliente
        String telaComOLink = escritorioLogado.perform(get(enderecoDoCaso)).andReturn().getResponse()
                .getContentAsString();
        Matcher achado = LINK_DO_CLIENTE.matcher(telaComOLink);
        assertThat(achado.find()).as("a tela do caso mostra o link do cliente").isTrue();
        String linkDoCliente = "/c/" + achado.group(1);
        confereATelaInicial(escritorioLogado, 0, 1, 0, null);

        //5. O cliente, sem conta, abre o link, vê o prazo e confirma marcando a data limite (registro de ciência)
        cliente.perform(get(linkDoCliente))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Padaria do Caminho")))
                .andExpect(content().string(containsString(dataNaTela)))
                .andExpect(content().string(containsString("Livro caixa de 2025")));
        cliente.perform(post(linkDoCliente).with(csrf()).param("resposta", dataLimite.toString()))
                .andExpect(redirectedUrl(linkDoCliente + "#confirmar"));
        cliente.perform(get(linkDoCliente))
                .andExpect(content().string(containsString("Recebimento confirmado")));
        //Confirmado com a data certa: o caso fica pronto para o escritório fechar
        confereATelaInicial(escritorioLogado, 0, 0, 1, null);

        //6. Fechar o caso: o escritório registra o desfecho e o que foi feito
        escritorioLogado.perform(post(enderecoDoCaso + "/resolver").param("desfecho", "ARQUIVADO")
                .param("observacao", "Livros entregues na Receita."))
                .andExpect(redirectedUrl(enderecoDoCaso));
        //Some das abertas e aparece nas resolvidas
        confereATelaInicial(escritorioLogado, 0, 0, 0, null);
        escritorioLogado.perform(get("/").param("ver", "resolvidas"))
                .andExpect(model().attribute("resolvidas", hasSize(1)));

        //Conferir: o histórico do caso conta o caminho inteiro, na ordem, com quem fez cada passo
        escritorioLogado.perform(get(enderecoDoCaso))
                .andExpect(content().string(containsString("Enviou o PDF")))
                .andExpect(content().string(containsString("Calculou o prazo: data limite " + dataNaTela)))
                .andExpect(content().string(containsString("Gerou o link para o cliente")))
                .andExpect(content().string(containsString("Confirmou a ciência marcando " + dataNaTela)))
                .andExpect(content().string(containsString("Resolveu o caso. Desfecho: Arquivado")));
        //Conta só os eventos deste caso: o banco H2 dos testes é o mesmo para todas as classes
        assertThat(banco.contarEventosDo(caso.getId())).isEqualTo(5);
    }

    //A tela inicial separa os casos abertos pelo próximo passo; "falta" é a etiqueta do caso sem aviso
    private void confereATelaInicial(MockMvc escritorioLogado, int precisaDeGente, int esperandoOCliente,
            int prontoParaFechar, String falta) throws Exception {
        var tela = escritorioLogado.perform(get("/"))
                .andExpect(model().attribute("precisaDeGente", hasSize(precisaDeGente)))
                .andExpect(model().attribute("esperandoOCliente", hasSize(esperandoOCliente)))
                .andExpect(model().attribute("prontoParaFechar", hasSize(prontoParaFechar)));
        if (falta != null) {
            tela.andExpect(content().string(containsString(falta)));
        }
    }

    private MockMultipartFile pdfDeExemplo() throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/pdfs/notificacao-acentuada.pdf").readAllBytes();
        return new MockMultipartFile("arquivo", "notificacao-acentuada.pdf", MediaType.APPLICATION_PDF_VALUE, bytes);
    }
}
