package br.com.namtech.atalaia.seguranca;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

//As regras de quem entra onde. O Spring Security fica na frente de todo pedido:
//sem sessão aberta, a pessoa é levada para a tela de entrar
@Configuration
public class ConfiguracaoDeSeguranca {

    @Bean
    SecurityFilterChain regras(HttpSecurity http, @Qualifier("limiteDeTentativas") LimiteDeTentativas limite)
            throws Exception {
        //O que o Spring faria sozinho depois de entrar: voltar para a página que a pessoa tentou abrir, ou para a inicial
        SavedRequestAwareAuthenticationSuccessHandler voltarParaAPagina = new SavedRequestAwareAuthenticationSuccessHandler();
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
                        .failureUrl("/entrar?erro")
                        //Senha certa: devolve a vaga que a TravaDeTentativas reservou, e segue o caminho de sempre
                        .successHandler((pedido, resposta, autenticacao) -> {
                            limite.devolver(pedido.getRemoteAddr());
                            voltarParaAPagina.onAuthenticationSuccess(pedido, resposta, autenticacao);
                        }))
                //A trava roda dentro do Spring Security (depois do firewall dele) e logo antes de conferir a senha
                .addFilterBefore(new TravaDeTentativas(limite), UsernamePasswordAuthenticationFilter.class)
                //Envio de arquivo de quem não entrou para antes do CSRF, que leria o arquivo inteiro atrás do código
                .addFilterBefore(new ArquivoSoComSessao(), CsrfFilter.class)
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

    //A conta de envios de PDF por escritório (issue #144): cada envio custa uma chamada à IA. Usa a mesma peça da
    //trava do login, com outra chave (o número do escritório, em vez do endereço) e outro máximo: 60 a cada 15 min
    //dá folga para um escritório que despeja a remessa do mês e para quem tenta gastar a IA à toa
    //Limite conhecido (revisão do Hefesto): quem cria várias contas soma as cotas; fica registrado na issue #144
    static final int ENVIOS_POR_JANELA = 60;

    @Bean
    LimiteDeTentativas limiteDeEnvios(Clock relogio) {
        return new LimiteDeTentativas(ENVIOS_POR_JANELA, LimiteDeTentativas.TETO_PADRAO, relogio);
    }

    //Embaralha a senha com bcrypt antes de guardar, e sabe conferir depois
    @Bean
    PasswordEncoder embaralhador() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
