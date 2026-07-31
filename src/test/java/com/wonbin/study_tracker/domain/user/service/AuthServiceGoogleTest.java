package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GoogleIdentityResolver googleIdentityResolver;

    @InjectMocks
    private AuthService authService;

    @Test
    void 처음_로그인하는_구글_사용자는_새로_생성된다() {
        when(googleIdentityResolver.resolveFromIdToken("id-token"))
                .thenReturn(new GoogleIdentity("google-sub-1", "new@example.com", "새유저"));
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .email(u.getEmail())
                    .name(u.getName())
                    .googleId(u.getGoogleId())
                    .dayChangeHour(5)
                    .build();
        });
        when(jwtProvider.generateAccessToken(1L, "new@example.com")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");

        AuthResponse.Token result = authService.loginWithGoogleIdToken("id-token");

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 이미_있는_구글_사용자는_재생성하지_않는다() {
        User existing = User.builder()
                .id(5L)
                .email("existing@example.com")
                .name("기존유저")
                .googleId("google-sub-2")
                .dayChangeHour(5)
                .build();

        when(googleIdentityResolver.resolveFromIdToken("id-token"))
                .thenReturn(new GoogleIdentity("google-sub-2", "existing@example.com", "기존유저"));
        when(userRepository.findByGoogleId("google-sub-2")).thenReturn(Optional.of(existing));
        when(jwtProvider.generateAccessToken(5L, "existing@example.com")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("refresh-token");

        authService.loginWithGoogleIdToken("id-token");

        verify(userRepository, never()).save(any(User.class));
    }
}
