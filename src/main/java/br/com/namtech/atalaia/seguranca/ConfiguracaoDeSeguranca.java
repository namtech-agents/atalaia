package br.com.namtech.atalaia.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

//As regras de quem entra onde. O Spring Security fica na frente de todo pedido:
//sem sessão aberta, a pessoa é levada para a tela de entrar
@Configuration
public class ConfiguracaoDeSeguranca {

    @Bean
    SecurityFilterChain regras(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(pedidos -> pedidos
                        //Abertos: entrar, criar conta, o que as telas carregam (css, js, letra, logo), a pagina de erro,
                        //a saude do sistema e a pagina do cliente (/c/ + token: quem abre é o dono da empresa, que nao
                        //tem conta; o segredo do link é a chave). Todo o resto exige sessão
                        .requestMatchers("/entrar", "/cadastro", "/css/**", "/js/**", "/fontes/**", "/logo/**", "/error",
                                "/actuator/health", "/c/*")
                        .permitAll()
                        .anyRequest().authenticated())
                //A tela de entrar é nossa (entrar.html); conferir e-mail e senha é com o Spring Security
                .formLogin(entrada -> entrada
                        .loginPage("/entrar")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .failureUrl("/entrar?erro"))
                //Sair é por POST (um link nao serve: outro site poderia deslogar a pessoa com uma imagem)
                .logout(saida -> saida
                        .logoutUrl("/sair")
                        .logoutSuccessUrl("/entrar?saiu"))
                //Os cabeçalhos de segurança sao do CabecalhosDeSeguranca. Aqui só se troca o padrao do Spring
                //(proibir qualquer moldura) por "só do proprio site", senao o PDF nao aparece ao lado do caso
                .headers(cabecalhos -> cabecalhos.frameOptions(moldura -> moldura.sameOrigin()));
        //A proteção CSRF ja vem ligada: todo formulario leva um codigo escondido que só a nossa pagina conhece
        return http.build();
    }

    //Embaralha a senha com bcrypt antes de guardar, e sabe conferir depois
    @Bean
    PasswordEncoder embaralhador() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
