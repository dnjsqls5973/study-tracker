package com.wonbin.study_tracker.global.security.filter;

import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtProvider jwtProvider;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ACCESS_토큰은_ROLE_ACCESS_권한을_부여받는다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(jwtProvider.validateToken("access-token")).thenReturn(true);
        when(jwtProvider.getTokenType("access-token")).thenReturn("ACCESS");
        when(jwtProvider.getUserId("access-token")).thenReturn(1L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ACCESS");
    }

    @Test
    void DEVICE_토큰은_ROLE_DEVICE_권한을_부여받는다() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer device-token");
        when(jwtProvider.validateToken("device-token")).thenReturn(true);
        when(jwtProvider.getTokenType("device-token")).thenReturn("DEVICE");
        when(jwtProvider.getUserId("device-token")).thenReturn(2L);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);
        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(2L);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_DEVICE");
    }
}
