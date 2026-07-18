package com.efinanceiro.configuracao;

import com.efinanceiro.seguranca.FiltroAutenticacaoJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class ConfiguracaoSeguranca {

    private final FiltroAutenticacaoJwt filtroAutenticacaoJwt;
    private final CorsConfigurationSource corsConfigurationSource;

    public ConfiguracaoSeguranca(FiltroAutenticacaoJwt filtroAutenticacaoJwt,
                                  CorsConfigurationSource corsConfigurationSource) {
        this.filtroAutenticacaoJwt = filtroAutenticacaoJwt;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Define a cadeia de filtros de segurança da aplicação: desabilita CSRF (API stateless),
     * libera as rotas de autenticação, exige token JWT para o restante da API e responde
     * 401 (em vez do 403 padrão do Spring Security) quando a requisição não traz token nenhum.
     *
     * @param http Configuração HTTP do Spring Security
     * @return Cadeia de filtros configurada
     */
    @Bean
    public SecurityFilterChain filtroSegurancaCadeia(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/autenticacao/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(filtroAutenticacaoJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Codificador de senha usado no cadastro e na autenticação de usuários.
     *
     * @return Instância de BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
