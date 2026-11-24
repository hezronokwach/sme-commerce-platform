package com.hezron.smecommerce;

import com.hezron.smecommerce.shared.AccessTokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Adds the shop to every log line of a request: the tenant id from the token in the admin, or the web
 * name from the address on the storefront. Runs after security, so the token has been checked.
 */
@Component
class LogContextInterceptor implements AsyncHandlerInterceptor {

    static final String MDC_KEY = "shop";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String shop = shopFromToken();
        if (shop == null) {
            shop = shopFromAddress(request);
        }
        if (shop != null) {
            MDC.put(MDC_KEY, shop);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        MDC.remove(MDC_KEY);
    }

    /** A live stream leaves this thread while it stays open: the thread must not keep the shop. */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        MDC.remove(MDC_KEY);
    }

    private static String shopFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString(AccessTokenClaims.TENANT_ID);
        }
        return null;
    }

    private static String shopFromAddress(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> values)) {
            return null;
        }
        Object slug = values.get("slug");
        if (slug == null) {
            slug = values.get("shopSlug");
        }
        // It comes from the address, so only a plain web name may reach the logs.
        if (slug != null && slug.toString().matches("[a-zA-Z0-9-]{1,50}")) {
            return slug.toString();
        }
        return null;
    }

}
