package com.wonbin.study_tracker.api.auth;

import com.wonbin.study_tracker.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 웹: Google Identity Services에서 받은 ID 토큰으로 로그인
    @PostMapping("/google")
    public ResponseEntity<AuthResponse.Token> loginWithGoogleIdToken(
            @Valid @RequestBody AuthRequest.GoogleIdTokenLogin request) {
        return ResponseEntity.ok(authService.loginWithGoogleIdToken(request.getIdToken()));
    }

    // Chrome Extension: chrome.identity.getAuthToken()으로 받은 액세스 토큰으로 로그인
    @PostMapping("/google/token")
    public ResponseEntity<AuthResponse.Token> loginWithGoogleAccessToken(
            @Valid @RequestBody AuthRequest.GoogleAccessTokenLogin request) {
        return ResponseEntity.ok(authService.loginWithGoogleAccessToken(request.getAccessToken()));
    }

    @PostMapping("/device")
    public ResponseEntity<AuthResponse.DeviceToken> registerDevice(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AuthRequest.DeviceRegister register) {
        return ResponseEntity.ok(authService.registerDevice(userId, register));
    }

    @PatchMapping("/device/push-token")
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AuthRequest.PushTokenUpdate request) {
        authService.registerPushToken(userId, request);
        return ResponseEntity.noContent().build();
    }

    // REFRESH 토큰으로 새 ACCESS 토큰 재발급
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse.Token> refresh(
            @Valid @RequestBody AuthRequest.RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.getRefreshToken()));
    }

}
