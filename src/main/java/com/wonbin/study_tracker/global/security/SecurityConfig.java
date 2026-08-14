package com.wonbin.study_tracker.global.security;

import com.wonbin.study_tracker.global.security.filter.JwtAuthenticationFilter;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 기본 AuthenticationEntryPoint(Http403ForbiddenEntryPoint)는 토큰이 없거나 만료/위조된
                // 요청에도 403을 반환한다. 클라이언트가 "ACCESS 토큰 만료 -> REFRESH로 재발급"을
                // 판단하려면 최소한 그 경우엔 401이 와야 하므로 명시적으로 설정한다.
                // 실측 확인: JwtAuthenticationFilter가 직접 구성한 Authentication(AuthenticationManager를
                // 거치지 않음)에 대해서는, 토큰은 유효하지만 역할이 부족한 경우(예: DEVICE 토큰으로
                // ROLE_ACCESS 전용 API 호출)도 Spring Security가 이 401 엔트리포인트로 함께 라우팅한다 —
                // "권한 부족은 항상 403"이라는 통념과 다름. 기능적 차단 자체는 두 경우 모두 동일하게
                // 이뤄지고 어떤 클라이언트도 401/403 구분에 의존하지 않아 문제 없음 (SecurityConfigTest 참고).
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/google",
                                "/api/auth/google/token",
                                "/api/auth/refresh",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").hasAuthority("ROLE_ACCESS")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/me").hasAuthority("ROLE_ACCESS")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me/day-change-hour").hasAuthority("ROLE_ACCESS")
                        .requestMatchers("/api/classifications/**").hasAuthority("ROLE_ACCESS")
                        .requestMatchers("/api/stats/**").hasAuthority("ROLE_ACCESS")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

}
