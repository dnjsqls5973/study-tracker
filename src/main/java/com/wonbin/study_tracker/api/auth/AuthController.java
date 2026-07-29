package com.wonbin.study_tracker.api.auth;

import com.wonbin.study_tracker.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse.Token> register(
            @Valid @RequestBody AuthRequest.Register request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse.Token> login(
            @Valid @RequestBody AuthRequest.Login request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/device")
    public ResponseEntity<AuthResponse.DeviceToken> registerDevice(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AuthRequest.DeviceRegister register) {
        return ResponseEntity.ok(authService.registerDevice(userId, register));
    }

}
