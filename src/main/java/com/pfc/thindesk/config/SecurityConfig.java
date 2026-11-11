package com.pfc.thindesk.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguranca herdada da atividade de Login Seguro (BCrypt + perfis no MongoDB),
 * com as melhorias:
 *  - CSRF ligado nos formularios web (login, logout, cadastro, edicao);
 *  - API (/api/**) responde 401/403 em JSON em vez de redirecionar;
 *  - API aceita HTTP Basic (facilita testes com curl/Postman);
 *  - Dashboard restrito a ADMIN e MANAGER (gestor).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // A API e consumida via fetch() na mesma origem e via Basic (curl/Postman).
            // O cookie de sessao e SameSite=Lax (application.properties), o que impede
            // que outro site dispare POST/PATCH autenticados. Formularios continuam com CSRF.
            .csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfig::isApi))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/cadastro", "/css/**", "/js/**", "/images/**", "/error").permitAll()
                .requestMatchers("/dashboard", "/api/v1/dashboard/**").hasAnyRole("ADMIN", "MANAGER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll())
            .httpBasic(Customizer.withDefaults())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    if (isApi(req)) {
                        erroJson(req, res, 401, "Unauthorized", "Autenticação necessária");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/login");
                    }
                })
                .accessDeniedHandler((req, res, e) -> {
                    if (isApi(req)) {
                        erroJson(req, res, 403, "Forbidden", "Seu perfil não tem acesso a este recurso");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/?acessoNegado=true");
                    }
                }));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static boolean isApi(HttpServletRequest req) {
        return req.getRequestURI().startsWith(req.getContextPath() + "/api/");
    }

    private static void erroJson(HttpServletRequest req, HttpServletResponse res, int status,
                                 String erro, String mensagem) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String caminho = req.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
        res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"erro\":\"" + erro + "\",\"mensagem\":\"" + mensagem
                + "\",\"caminho\":\"" + caminho + "\",\"detalhes\":[]}");
    }
}
