package com.hezron.smecommerce.payments.internal.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal chain for payment provider callbacks. Safaricom and Stripe can't send our JWT, so these
 * paths are open at the HTTP level; each callback proves itself in the handler instead (secret path
 * segment, known pending payment, matching amount, Stripe signature).
 * <p>
 * {@code @Order(1)} makes it run before identity's main API chain, which would otherwise demand a token.
 */
@Configuration(proxyBeanMethods = false)
class WebhookSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/api/webhooks/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

}
