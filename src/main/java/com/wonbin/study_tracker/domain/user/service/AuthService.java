package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthRequest;
import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final JwtProvider jwtProvider;
    private final GoogleIdentityResolver googleIdentityResolver;

    // 웹: Google ID 토큰으로 로그인
    @Transactional
    public AuthResponse.Token loginWithGoogleIdToken(String idToken) {
        GoogleIdentity identity = googleIdentityResolver.resolveFromIdToken(idToken);
        return issueTokensForGoogleUser(identity);
    }

    // Chrome Extension: Google 액세스 토큰으로 로그인
    @Transactional
    public AuthResponse.Token loginWithGoogleAccessToken(String accessToken) {
        GoogleIdentity identity = googleIdentityResolver.resolveFromAccessToken(accessToken);
        return issueTokensForGoogleUser(identity);
    }

    private AuthResponse.Token issueTokensForGoogleUser(GoogleIdentity identity) {
        User user = userRepository.findByGoogleId(identity.googleId())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(identity.email())
                                .name(identity.name() != null ? identity.name() : identity.email())
                                .googleId(identity.googleId())
                                .dayChangeHour(5)
                                .build()
                ));

        return AuthResponse.Token.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    @Transactional
    public AuthResponse.DeviceToken registerDevice(Long userId, AuthRequest.DeviceRegister request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String deviceToken = jwtProvider.generateDeviceToken(userId, request.getDeviceName());

        Device device = Device.builder()
                .user(user)
                .deviceName(request.getDeviceName())
                .deviceType(request.getDeviceType())
                .deviceToken(deviceToken)
                .build();

        deviceRepository.save(device);

        return AuthResponse.DeviceToken.builder()
                .deviceToken(deviceToken)
                .deviceId(device.getId())
                .build();
    }

    @Transactional
    public void registerPushToken(Long userId, AuthRequest.PushTokenUpdate request) {
        Device device = deviceRepository.findById(Long.parseLong(request.getDeviceId()))
                .orElseThrow(() -> new IllegalArgumentException("기기를 찾을 수 없습니다."));

        if (!device.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        device.updatePushToken(request.getPushToken());
    }

    // REFRESH 토큰은 재발급하지 않고 그대로 반환한다
    @Transactional(readOnly = true)
    public AuthResponse.Token refreshAccessToken(String refreshToken) {
        String tokenType;
        Long userId;
        try {
            tokenType = jwtProvider.getTokenType(refreshToken);
            userId = jwtProvider.getUserId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }

        if (!"REFRESH".equals(tokenType)) {
            throw new IllegalArgumentException("REFRESH 토큰이 아닙니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return AuthResponse.Token.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .build();
    }
}
