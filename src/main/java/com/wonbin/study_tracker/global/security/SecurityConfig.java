package com.wonbin.study_tracker.global.security;

import com.wonbin.study_tracker.global.security.filter.JwtAuthenticationFilter;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/google",
                                "/api/auth/google/token",
                                "/api/auth/refresh",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/me").hasAuthority("ROLE_ACCESS")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me/day-change-hour").hasAuthority("ROLE_ACCESS")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

}
