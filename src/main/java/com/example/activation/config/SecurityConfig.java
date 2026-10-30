package com.example.activation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Resource Server OAuth 2.0: valida JWTs emitidos pelo servidor de autorização (assinatura via JWKS,
 * emissor, audiência e expiração, configurados em {@code spring.security.oauth2.resourceserver.jwt}).
 *
 * @since 2026-10-29
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Autoridade exigida para consultar e listar (escopo {@code activation:read} do token).
     */
    public static final String SCOPE_READ = "SCOPE_activation:read";

    /**
     * Autoridade exigida para criar (escopo {@code activation:write} do token).
     */
    public static final String SCOPE_WRITE = "SCOPE_activation:write";

    /**
     * API stateless protegida por JWT: escrita exige {@value #SCOPE_WRITE}, leitura exige {@value #SCOPE_READ};
     * health checks e documentação OpenAPI são públicos; qualquer outra rota é negada.
     *
     * @param http configuração de segurança HTTP
     * @return cadeia de filtros de segurança
     * @throws Exception se a configuração for inválida
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/requests").hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/requests", "/api/v1/requests/*")
                        .hasAuthority(SCOPE_READ)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * O {@code sub} identifica o sistema consumidor (ver {@link CurrentClientId}); token sem ele é inválido (401).
     * O Spring Boot adiciona este validador aos que já verifica (assinatura, emissor, audiência, expiração).
     */
    @Bean
    OAuth2TokenValidator<Jwt> subjectRequiredValidator() {
        return new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText);
    }
}
