package com.example.activation.handler;

import com.example.activation.dto.FieldViolation;
import com.example.activation.exception.DuplicateRequestConflictException;
import com.example.activation.exception.InvalidStatusTransitionException;
import com.example.activation.exception.RequestNotFoundException;
import com.example.activation.utils.CorrelationIdUtils;
import com.example.activation.utils.LogBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Todas as respostas de erro seguem o Problem Details (RFC 9457) e levam o {@code traceId}
 * (identificador de correlação) para localizar o erro nos logs. Detalhes internos nunca são expostos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_BASE_URI = "https://api.exemplo.com/problems/";

    @ExceptionHandler(RequestNotFoundException.class)
    ProblemDetail handleNotFound(RequestNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Solicitação não encontrada", ex.getMessage());
    }

    @ExceptionHandler(DuplicateRequestConflictException.class)
    ProblemDetail handleDuplicate(DuplicateRequestConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "duplicate-request", "Solicitação duplicada", ex.getMessage());
        problem.setProperty("existingRequestId", ex.getExistingRequestId());
        return problem;
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleStateConflict(RuntimeException ex) {
        LogBuilder.warn(log, "State conflict")
                .with("reason", ex.getMessage())
                .log();
        return problem(HttpStatus.CONFLICT, "state-conflict", "Conflito de estado",
                "A solicitação foi alterada por outra operação. Tente novamente.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LogBuilder.error(log, "Unexpected error")
                .exception(ex)
                .log();
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro inesperado",
                "Ocorreu um erro inesperado. Informe o traceId ao suporte.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                           HttpHeaders headers,
                                                                           HttpStatusCode status,
                                                                           WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("traceId"))) {
            problem.setProperty("traceId", CorrelationIdUtils.current());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail validationProblem(List<FieldViolation> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos",
                "Um ou mais campos não passaram na validação.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE_URI + type));
        problem.setTitle(title);
        problem.setProperty("traceId", CorrelationIdUtils.current());
        return problem;
    }
}
