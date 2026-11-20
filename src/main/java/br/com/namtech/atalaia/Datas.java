package br.com.namtech.atalaia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

//Apoio para ler data que chega de um formulario e para escrever data como se escreve aqui
public final class Datas {

    //09/12/2026
    public static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Datas() {
    }

    //O campo de data do navegador manda 2026-11-18. Devolve null se o texto nao for uma data
    public static LocalDate ler(String texto) {
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException erro) {
            return null;
        }
    }
}
