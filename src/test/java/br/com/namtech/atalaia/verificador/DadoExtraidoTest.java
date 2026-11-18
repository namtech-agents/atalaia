package br.com.namtech.atalaia.verificador;

import static org.assertj.core.api.Assertions.assertThat;

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
}
