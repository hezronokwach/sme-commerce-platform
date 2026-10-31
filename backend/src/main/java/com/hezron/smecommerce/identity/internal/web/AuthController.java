package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.AuthService;
import com.hezron.smecommerce.identity.internal.application.AuthTokens;
import com.hezron.smecommerce.identity.internal.application.SignUpResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public account endpoints under {@code /api/auth}, which the security chain leaves open.
 * Only HTTP work happens here: turn JSON into a command, call the service, turn the result into JSON
 * and the refresh cookie.
 * <p>
 * {@code ResponseEntity} instead of returning the body directly: these responses also need a header
 * (Set-Cookie).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResult result = authService.signUp(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.with(result.tokens()))
                .body(SignUpResponse.from(result));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(authService.login(request.toCommand()));
    }

    // required = false: a missing cookie gets our 401 problem JSON instead of Spring's generic 400.
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withTokens(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared())
                .build();
    }

    private static ResponseEntity<TokenResponse> withTokens(AuthTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.with(tokens))
                .body(TokenResponse.from(tokens));
    }

}
