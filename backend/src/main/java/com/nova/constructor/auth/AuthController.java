package com.nova.constructor.auth;

import com.nova.constructor.auth.AuthDtos.LoginRequest;
import com.nova.constructor.auth.AuthDtos.RefreshRequest;
import com.nova.constructor.auth.AuthDtos.RegisterRequest;
import com.nova.constructor.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request,
                                  @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return auth.register(request, userAgent);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return auth.login(request, userAgent);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 @org.springframework.web.bind.annotation.RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return auth.refresh(request.refreshToken(), userAgent);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }
}
