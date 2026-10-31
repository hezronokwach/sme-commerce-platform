package com.hezron.smecommerce.identity.internal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The main API filter chain. It handles every request that no more specific chain claimed first
 * (for example payments' webhook chain at {@code @Order(1)}), which is why it runs last.
 * <p>
 * Endpoint-level rules such as {@code @PreAuthorize("hasRole('OWNER')")} live on each module's own
 * controllers; {@code @EnableMethodSecurity} switches them on.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    // No @Order on purpose: an unordered chain sorts last, after every module's more specific chain.
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        http
                // Tokens travel in a header the browser never adds by itself, so there is nothing for CSRF to abuse.
                .csrf(AbstractHttpConfigurer::disable)
                // No server-side session: every request proves itself with its token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Spring forwards failed requests here to render the error; keep it reachable.
                        .requestMatchers("/error").permitAll()
                        // Storefront is public; the shop-from-slug filter comes with the tenant-context issue.
                        .requestMatchers("/api/store/**").permitAll()
                        // A valid token isn't enough: it must be a staff token (customer tokens will carry
                        // another role), so a storefront login can never open the admin API.
                        .requestMatchers("/api/admin/**").hasAnyRole("OWNER", "STAFF")
                        .anyRequest().denyAll())
                // Reads "Authorization: Bearer ...", checks it with the JwtDecoder bean, 401 if anything is wrong.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * bcrypt by default. Hashes are stored with an id prefix ({@code {bcrypt}...}) so the algorithm can
     * change later and old hashes still verify.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

}
