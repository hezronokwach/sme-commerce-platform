package com.hezron.smecommerce;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives every request an id that appears in every log line it writes, in the response header, and in
 * every error body. A user reports "error 7f3c...", and that id finds the full story in the logs.
 * <p>
 * Runs first, before security, so even a 401 has an id. Lives next to the application class: it is
 * app-wide wiring, not part of any module.
 * <p>
 * A plain servlet {@link Filter} rather than Spring's {@code OncePerRequestFilter}: that one has final
 * methods, which break when the bean is wrapped in a proxy (as happens in the MockMvc tests).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";

    /** Key under which logs and the error handler find the id (log pattern: {@code %X{correlationId}}). */
    public static final String MDC_KEY = "correlationId";

    /**
     * A caller (the frontend, a gateway) may send its own id so one action can be traced across systems.
     * Only short, plain ids are accepted: anything else could forge or break log lines, so we make our own.
     */
    private static final int MAX_ID_LENGTH = 64;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String correlationId = idFrom(request.getHeader(HEADER));
        response.setHeader(HEADER, correlationId);
        // MDC is SLF4J's per-thread map of values added to every log line.
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are reused for the next request: never leave this request's id behind.
            MDC.remove(MDC_KEY);
        }
    }

    private static String idFrom(String header) {
        if (isSafeId(header)) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    /** 1 to 64 characters, each a letter, digit, dot, underscore or hyphen. No spaces or newlines. */
    private static boolean isSafeId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            boolean isLetter = (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
            boolean isDigit = character >= '0' && character <= '9';
            boolean isAllowedSymbol = character == '.' || character == '_' || character == '-';
            if (!isLetter && !isDigit && !isAllowedSymbol) {
                return false;
            }
        }
        return true;
    }

}
