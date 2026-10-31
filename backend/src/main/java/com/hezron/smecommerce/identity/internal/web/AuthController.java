package com.hezron.smecommerce.identity.internal.web;

import com.hezron.smecommerce.identity.internal.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public account endpoints under {@code /api/auth}, which the security chain leaves open.
 * Only HTTP work happens here: turn JSON into a command, call the service, turn the result into JSON.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return SignUpResponse.from(authService.signUp(request.toCommand()));
    }

}
