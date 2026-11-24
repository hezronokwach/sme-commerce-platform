package com.hezron.smecommerce.identity.internal.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

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
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errorResolver) {
        // 401 and 403 happen in these filters, before any controller, so the app's error handler would
        // never see them. Passing them to Spring MVC's exception resolver sends them to the same handler,
        // so they get the same JSON shape as every other error.
        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint whenNotLoggedIn = (request, response, exception) -> {
            // Sets 401 and the standard "WWW-Authenticate: Bearer ..." header; the resolver writes the body.
            bearerEntryPoint.commence(request, response, exception);
            errorResolver.resolveException(request, response, null, exception);
        };
        AccessDeniedHandler whenNotAllowed =
                (request, response, exception) -> errorResolver.resolveException(request, response, null, exception);

        http
                // Browsers may only call this API from our own two frontends (the bean below).
                .cors(Customizer.withDefaults())
                // Tokens travel in a header the browser never adds by itself, so there is nothing for CSRF to abuse.
                .csrf(AbstractHttpConfigurer::disable)
                // No server-side session: every request proves itself with its token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Metrics cover every shop, so they are only readable on the server itself.
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                        .access(SecurityConfig::onlyFromThisMachine)
                        // The API description and Swagger UI only describe endpoints; each endpoint still
                        // checks its own token. (The deployment issue decides whether prod hides them.)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Spring forwards failed requests here to render the error; keep it reachable.
                        .requestMatchers("/error").permitAll()
                        // Storefront is public; each endpoint finds its shop from the slug in the URL.
                        .requestMatchers("/api/store/**").permitAll()
                        // The admin's live order feed (ADR 0012): the browser's EventSource can't send a
                        // token, so a one-time SHOP ticket in the address proves who it is, and only staff
                        // can get one. Just this GET; the ticket request itself needs a staff token below.
                        .requestMatchers(HttpMethod.GET, "/api/admin/order-feed").permitAll()
                        // A valid token isn't enough: it must be a staff token (customer tokens will carry
                        // another role), so a storefront login can never open the admin API.
                        .requestMatchers("/api/admin/**").hasAnyRole("OWNER", "STAFF")
                        .anyRequest().denyAll())
                // Reads "Authorization: Bearer ...", checks it with the JwtDecoder bean, 401 if anything is wrong.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(whenNotLoggedIn)
                        .accessDeniedHandler(whenNotAllowed))
                // Same for requests with no token at all (for example a path that is denyAll).
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(whenNotLoggedIn)
                        .accessDeniedHandler(whenNotAllowed));
        return http.build();
    }

    /**
     * Which websites may call this API from a browser. Set by CORS_ALLOWED_ORIGINS when deployed.
     * Credentials are allowed because the refresh token travels in a cookie.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration rules = new CorsConfiguration();
        rules.setAllowedOrigins(origins);
        rules.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        rules.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Order-Token", "X-Correlation-Id"));
        rules.setExposedHeaders(List.of("X-Correlation-Id"));
        rules.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", rules);
        return source;
    }

    private static AuthorizationDecision onlyFromThisMachine(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        try {
            InetAddress caller = InetAddress.getByName(context.getRequest().getRemoteAddr());
            return new AuthorizationDecision(caller.isLoopbackAddress());
        } catch (UnknownHostException notAnAddress) {
            return new AuthorizationDecision(false);
        }
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
