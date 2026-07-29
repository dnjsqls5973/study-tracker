package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthRequest;
import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public AuthResponse.Token register(AuthRequest.Register request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .dayChangeHour(5)
                .build();

        userRepository.save(user);

        return AuthResponse.Token.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    // 로그인
    @Transactional(readOnly = true)
    public AuthResponse.Token login(AuthRequest.Login request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return AuthResponse.Token.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .name(user.getName())
                .build();
    }

    // device Token 발급
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
}
