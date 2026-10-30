package com.example.activation.config;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta o identificador do sistema consumidor autenticado: o claim {@code sub} do JWT
 * (no client credentials, o client_id). A presença do claim é garantida pela validação do token
 * em {@link SecurityConfig}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "subject")
public @interface CurrentClientId {
}
