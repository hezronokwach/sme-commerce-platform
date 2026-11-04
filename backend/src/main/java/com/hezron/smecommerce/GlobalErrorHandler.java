package com.hezron.smecommerce;

import com.hezron.smecommerce.shared.BaseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error the API returns has one shape (Guide 1.4), whoever threw it:
 *
 * <pre>
 * { "title": "Product not found", "status": 404,
 *   "detail": "There is no product with this id in your shop.", "instance": "/api/admin/products/7f3c...",
 *   "code": "PRODUCT_NOT_FOUND", "correlationId": "1b9e...", "timestamp": "2026-11-03T08:00:00Z" }
 * </pre>
 *
 * title, status, detail and instance are the RFC 9457 standard ({@code detail} is the human-readable
 * message; its {@code type} field is left out while it's the default "about:blank");
 * {@code code} is for programs, {@code correlationId} finds the request in the logs. Validation errors
 * add {@code errors: [{field, message}]}.
 * <p>
 * Extends Spring's {@link ResponseEntityExceptionHandler}, so Spring's own errors (malformed JSON,
 * wrong HTTP method, ...) get the same shape too: they all pass through {@link #createResponseEntity}.
 */
@RestControllerAdvice
public class GlobalErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    /** Bean validation on a request body ({@code @Valid}): 400 with one entry per bad field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Some fields are missing or invalid.");
        problem.setTitle("Invalid input");
        problem.setProperty(BaseException.CODE_PROPERTY, "VALIDATION_FAILED");
        // One {field, message} entry per invalid field, e.g. {"field": "password", "message": "size must be ..."}.
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("field", fieldError.getField());
            error.put("message", messageOf(fieldError.getDefaultMessage()));
            errors.add(error);
        }
        problem.setProperty("errors", errors);
        return createResponseEntity(problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Two people changed the same row; the second save was based on an old version (@Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleConcurrentUpdate(WebRequest request) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Changed by someone else",
                "Someone else changed this while you were editing. Reload it and try again.", request);
    }

    /** No valid token. Reached from the security chain too (see identity's SecurityConfig). */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleUnauthenticated(WebRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Login required",
                "Log in to do this.", request);
    }

    /** Logged in, but not allowed (for example a STAFF user on an owner-only endpoint). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleForbidden(WebRequest request) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not allowed",
                "You don't have permission to do this.", request);
    }

    /**
     * A bug or an outage. The caller gets a generic message; the details go only to the log, where the
     * correlation id in the response finds them. Never send stack traces or SQL to a client.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong",
                "Something went wrong on our side. Quote the correlation id if you contact support.", request);
    }

    /**
     * The one place every error body passes through. Adds what RFC 9457 doesn't have: our code (when
     * the error didn't set one), the correlation id and the time.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail) {
            ProblemDetail problem = (ProblemDetail) body;

            if (!hasCode(problem)) {
                // Spring's own errors carry no code: use the status name, e.g. BAD_REQUEST, METHOD_NOT_ALLOWED.
                problem.setProperty(BaseException.CODE_PROPERTY, codeFromStatus(statusCode));
            }
            problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
            problem.setProperty("timestamp", Instant.now().toString());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String code, String title, String detail,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(BaseException.CODE_PROPERTY, code);
        return createResponseEntity(problem, new HttpHeaders(), status, request);
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null) {
            return false;
        }
        return properties.containsKey(BaseException.CODE_PROPERTY);
    }

    private static String codeFromStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return "ERROR";
        }
        return status.name();
    }

    private static String messageOf(@Nullable String message) {
        if (message == null) {
            return "is invalid";
        }
        return message;
    }

}
