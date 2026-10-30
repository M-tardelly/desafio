package com.example.activation.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Executa antes do Spring Security para que respostas 401/403 também tenham o identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTED_FORMAT = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var incoming = request.getHeader(CorrelationId.HEADER);
        var correlationId = incoming != null && ACCEPTED_FORMAT.matcher(incoming).matches()
                ? incoming
                : CorrelationId.generate();

        response.setHeader(CorrelationId.HEADER, correlationId);
        try (var ignored = CorrelationId.bind(correlationId)) {
            chain.doFilter(request, response);
        }
    }
}
