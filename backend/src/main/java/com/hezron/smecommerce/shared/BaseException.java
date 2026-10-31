package com.hezron.smecommerce.shared;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Parent of every error a module raises on purpose (slug taken, insufficient stock, ...).
 * <p>
 * Each one becomes an RFC 9457 problem response with the same shape everywhere:
 * {@code status}, {@code title}, {@code detail} and a machine-readable {@code code} such as
 * {@code SLUG_TAKEN} that the frontends can switch on. Extending Spring's
 * {@link ErrorResponseException} means Spring MVC renders it without any extra handler code.
 * <p>
 * Subclasses only pass their status, code, title and message; they hold no logic.
 */
@Getter
public abstract class BaseException extends ErrorResponseException {

    /** Name of the extra problem property carrying the error code. */
    public static final String CODE_PROPERTY = "code";

    /** Machine-readable error code, e.g. {@code SLUG_TAKEN}. */
    private final String code;

    protected BaseException(HttpStatus status, String code, String title, String detail) {
        super(status, problem(status, code, title, detail), null);
        this.code = code;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }

}
