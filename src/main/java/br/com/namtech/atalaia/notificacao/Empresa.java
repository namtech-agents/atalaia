package br.com.namtech.atalaia.notificacao;

import br.com.namtech.atalaia.conta.Escritorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

//Empresa: o cliente do escritório, dono da notificação. Cada objeto desta classe é uma linha da tabela empresa
@Entity
public class Empresa {

    //O banco é que gera o numero (identity)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //O escritório dono desta empresa. Toda busca de empresa passa por ele
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Escritorio escritorio;

    //14 posições em texto: o CNPJ novo (2026) pode ter letras
    @Column(length = 14)
    private String cnpj;

    @Column(length = 200)
    private String nome;

    //O JPA exige um construtor vazio para montar o objeto quando le do banco
    protected Empresa() {
    }

    //Pesos da conta de cada digito de conferencia (módulo 11), da Receita Federal
    private static final int[] PESOS_DO_PRIMEIRO = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_DO_SEGUNDO = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    //Confere os dois ultimos digitos, que sao calculados a partir dos outros: pega erro de digitação.
    //Recebe o CNPJ ja arrumado (14 posições, maiusculas). Vale para o CNPJ novo, com letras: cada posição
    //vale o codigo dela menos 48 ('0' vale 0, '9' vale 9, 'A' vale 17)
    public static boolean cnpjValido(String cnpj) {
        //Todas as posições iguais (00000000000000) passam na conta, mas nao sao CNPJ de ninguem
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        char primeiro = digitoDeConferencia(cnpj.substring(0, 12), PESOS_DO_PRIMEIRO);
        char segundo = digitoDeConferencia(cnpj.substring(0, 12) + primeiro, PESOS_DO_SEGUNDO);
        return cnpj.charAt(12) == primeiro && cnpj.charAt(13) == segundo;
    }

    //Soma cada posição vezes o seu peso; resto da divisão por 11 menor que 2 da 0, senao 11 menos o resto
    private static char digitoDeConferencia(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return (char) ('0' + (resto < 2 ? 0 : 11 - resto));
    }

    public Empresa(Escritorio escritorio, String cnpj, String nome) {
        this.escritorio = escritorio;
        this.cnpj = cnpj;
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public Escritorio getEscritorio() {
        return escritorio;
    }

    public String getCnpj() {
        return cnpj;
    }

    //O CNPJ como se escreve: 99.999.001/0001-00. Cada parentese pega um pedaço e o $ poe de volta com a pontuação
    public String getCnpjFormatado() {
        return cnpj.replaceFirst("(.{2})(.{3})(.{3})(.{4})(.{2})", "$1.$2.$3/$4-$5");
    }

    public String getNome() {
        return nome;
    }
}
