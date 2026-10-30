package com.example.activation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida as regras aplicadas pelo {@link JwtDecoder} da aplicação (o mesmo usado em produção),
 * sem precisar de servidor de autorização: a assinatura é a única etapa não exercitada aqui.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtValidationIT {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void acceptsTokenWithSubject() {
        OAuth2TokenValidatorResult result = validator().validate(token("system-a"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithoutSubject() {
        OAuth2TokenValidatorResult result = validator().validate(token(null));

        assertThat(result.getErrors())
                .anySatisfy(error -> assertThat(error.getDescription()).contains("sub"));
    }

    @SuppressWarnings("unchecked")
    private OAuth2TokenValidator<Jwt> validator() {
        return (OAuth2TokenValidator<Jwt>) ReflectionTestUtils.getField(jwtDecoder, "jwtValidator");
    }

    private static Jwt token(String subject) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://auth.dev.local")
                .audience(List.of("activation-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", "activation:read");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
