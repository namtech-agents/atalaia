package br.com.namtech.atalaia;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import br.com.namtech.atalaia.notificacao.Notificacao;

@SpringBootApplication
public class AtalaiaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtalaiaApplication.class, args);
	}

	//O relogio do Atalaia: um só para o sistema inteiro, no fuso de Teresina. Quem precisa do "agora" ou do "hoje"
	//pede este relogio ao Spring, em vez de olhar o do servidor; num teste da para trocar por um relogio parado
	@Bean
	Clock relogio() {
		return Clock.system(Notificacao.TERESINA);
	}

}
