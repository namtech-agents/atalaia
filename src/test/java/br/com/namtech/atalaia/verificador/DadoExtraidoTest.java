package br.com.namtech.atalaia.verificador;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class DadoExtraidoTest {

    @Test
    void rotuloTrocaONomeQueAIAEscreveuPeloNomeDaTela() {
        //Arrumar: um campo conhecido, um que a IA inventou e um que veio sem nome
        DadoExtraido conhecido = new DadoExtraido("numero_do_processo", "0001234-56.2026", "Processo nº 0001234-56.2026");
        DadoExtraido inventado = new DadoExtraido("orgao_emissor", "Receita Federal", "Receita Federal do Brasil");
        DadoExtraido semNome = new DadoExtraido(null, "30 dias", "Prazo: 30 dias");
        DadoExtraido emBranco = new DadoExtraido("  ", "30 dias", "Prazo: 30 dias");
        DadoExtraido comMaiuscula = new DadoExtraido(" NUMERO_DO_PROCESSO ", "0001234-56.2026", "Processo nº 0001234-56.2026");

        //Agir e conferir: o conhecido ganha o nome com acento; o inventado aparece como veio, só sem o sublinhado
        assertThat(conhecido.rotulo()).isEqualTo("Número do processo");
        assertThat(inventado.rotulo()).isEqualTo("orgao emissor");
        assertThat(semNome.rotulo()).isEqualTo("sem nome");
        assertThat(emBranco.rotulo()).isEqualTo("sem nome");
        //Maiuscula e espaço nas pontas nao atrapalham
        assertThat(comMaiuscula.rotulo()).isEqualTo("Número do processo");
    }

    @Test
    void soOsSeisCamposDoPedidoSaoConhecidos() {
        //Arrumar e agir e conferir: os seis do pedido valem, com maiuscula e espaço nas pontas tambem
        for (String campo : List.of("valor", "tributo", "numero_do_processo", "remetente", "destinatario", " PRAZO ")) {
            assertThat(new DadoExtraido(campo, "x", "x").conhecido()).as(campo).isTrue();
        }
        //Inventado, sem nome e o "parte" antigo (o pedido de hoje nao pede mais) nao valem
        for (String campo : Arrays.asList("orgao_emissor", null, "  ", "parte")) {
            assertThat(new DadoExtraido(campo, "x", "x").conhecido()).as(String.valueOf(campo)).isFalse();
        }
    }
}
