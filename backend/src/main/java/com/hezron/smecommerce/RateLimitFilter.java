package com.hezron.smecommerce;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limits the endpoints worth abusing, per IP address and minute (Guide 8.2). Runs right after
 * CorrelationIdFilter and before security, so a blocked request costs almost nothing, not even a
 * password check.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RateLimitFilter implements Filter {

    private static final int LOGINS_PER_MINUTE = 5;
    private static final int SIGNUPS_PER_MINUTE = 5;
    private static final int REFRESHES_PER_MINUTE = 30;
    private static final int ORDERS_PER_MINUTE = 20;
    private static final int PAYMENT_ATTEMPTS_PER_MINUTE = 10;

    private final boolean enabled;
    private final HandlerExceptionResolver errorResolver;
    private final RequestRateLimiter limiter = new RequestRateLimiter(Clock.systemUTC());

    RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errorResolver) {
        this.enabled = enabled;
        this.errorResolver = errorResolver;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (enabled && "POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            int limit = limitFor(path);
            if (limit > 0 && !limiter.tryAcquire(ruleName(path) + ":" + request.getRemoteAddr(), limit)) {
                // Through the app's error handler, so a 429 has the same JSON shape as every other error.
                errorResolver.resolveException(request, response, null, new TooManyRequestsException());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** How many POSTs a minute this path allows; 0 means it isn't limited here. */
    private static int limitFor(String path) {
        String rule = ruleName(path);
        if ("login".equals(rule)) {
            return LOGINS_PER_MINUTE;
        }
        if ("signup".equals(rule)) {
            return SIGNUPS_PER_MINUTE;
        }
        if ("refresh".equals(rule)) {
            return REFRESHES_PER_MINUTE;
        }
        if ("order".equals(rule)) {
            return ORDERS_PER_MINUTE;
        }
        if ("payment-attempt".equals(rule)) {
            return PAYMENT_ATTEMPTS_PER_MINUTE;
        }
        return 0;
    }

    private static String ruleName(String path) {
        if ("/api/auth/login".equals(path)) {
            return "login";
        }
        if ("/api/auth/signup".equals(path)) {
            return "signup";
        }
        if ("/api/auth/refresh".equals(path)) {
            return "refresh";
        }
        if (path.matches("/api/store/[^/]+/orders")) {
            return "order";
        }
        if (path.matches("/api/store/[^/]+/orders/[^/]+/payment-attempts")) {
            return "payment-attempt";
        }
        return "";
    }

}
