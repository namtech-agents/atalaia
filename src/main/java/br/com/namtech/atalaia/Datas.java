package br.com.namtech.atalaia;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

//Apoio para ler data que chega de um formulario
public final class Datas {

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
