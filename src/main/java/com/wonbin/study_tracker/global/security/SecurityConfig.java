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
                .cors(cors -> {})  // Cors 처리
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 기본 AuthenticationEntryPoint(Http403ForbiddenEntryPoint)는 "토큰 없음/만료/위조"와
                // "토큰은 유효하지만 권한 부족"을 구분하지 않고 둘 다 403을 반환한다. 클라이언트가
                // "ACCESS 토큰 만료 -> REFRESH로 재발급"을 판단하려면 이 둘이 401/403으로 구분되어야 하므로
                // 인증 자체가 안 된 경우(익명 사용자)는 401을 반환하도록 명시적으로 설정한다.
                // (권한은 있는데 역할이 부족한 경우, 예: DEVICE 토큰으로 ROLE_ACCESS 전용 API 호출은
                // 여전히 기본 AccessDeniedHandler가 403을 반환 — 의도한 그대로 유지됨.)
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
