package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthResponse;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// JwtProvider는 실제 인스턴스를 사용해 발급한 ACCESS 토큰을 다시 파싱까지 검증한다.
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    private static final String SECRET = "ZG9ja2VyLWxvY2FsLXRlc3Qtand0LXNlY3JldC1rZXktbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private GoogleIdentityResolver googleIdentityResolver;

    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, 1_800_000L, 604_800_000L);
        authService = new AuthService(userRepository, deviceRepository, jwtProvider, googleIdentityResolver);
    }

    @Test
    void 유효한_REFRESH_토큰으로_새_ACCESS_토큰을_발급받는다() {
        User user = User.builder().id(1L).email("a@a.com").name("에이").googleId("g1").dayChangeHour(5).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String refreshToken = jwtProvider.generateRefreshToken(1L);

        AuthResponse.Token result = authService.refreshAccessToken(refreshToken);

        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(jwtProvider.getTokenType(result.getAccessToken())).isEqualTo("ACCESS");
        assertThat(jwtProvider.getUserId(result.getAccessToken())).isEqualTo(1L);
        assertThat(result.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    void ACCESS_토큰으로_요청하면_거부된다() {
        String accessToken = jwtProvider.generateAccessToken(1L, "a@a.com");

        assertThatThrownBy(() -> authService.refreshAccessToken(accessToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DEVICE_토큰으로_요청하면_거부된다() {
        String deviceToken = jwtProvider.generateDeviceToken(1L, "내폰");

        assertThatThrownBy(() -> authService.refreshAccessToken(deviceToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 만료된_REFRESH_토큰은_거부된다() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("type", "REFRESH")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> authService.refreshAccessToken(expiredToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 형식이_잘못된_토큰은_거부된다() {
        assertThatThrownBy(() -> authService.refreshAccessToken("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
