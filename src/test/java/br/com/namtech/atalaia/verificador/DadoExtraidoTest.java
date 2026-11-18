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

        //Agir e conferir: o conhecido ganha o nome com acento; o inventado aparece como veio, só sem o sublinhado
        assertThat(conhecido.rotulo()).isEqualTo("Número do processo");
        assertThat(inventado.rotulo()).isEqualTo("orgao emissor");
        assertThat(semNome.rotulo()).isEqualTo("sem nome");
    }
}
