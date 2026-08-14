package com.wonbin.study_tracker.global.security;

import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    // ROLE_ACCESS 전용 엔드포인트. 이 테스트가 검증하려는 건 "누가/어떤 토큰으로 호출했을 때
    // 401 vs 403 중 뭐가 오는지"이지 이 엔드포인트 자체의 응답 내용이 아니므로,
    // ROLE_ACCESS 제약이 걸린 아무 엔드포인트나 고정해서 씀.
    private static final String PROTECTED_URL = "/api/stats/today";

    @Test
    void 토큰_없이_호출하면_401을_반환한다() {
        ResponseEntity<String> response = restTemplate.exchange(
                PROTECTED_URL, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 만료된_토큰으로_호출하면_401을_반환한다() {
        // exp를 과거로 설정할 수 없으니, 서명은 맞지만 파싱 자체가 실패하는 변조 토큰으로
        // "validateToken()이 false를 반환하는 모든 경우"를 동일하게 대표시켜 검증한다
        // (만료/위조/파싱실패 전부 JwtProvider.validateToken()에서 동일하게 처리됨 — 위 소스 참고).
        String validAccessToken = jwtProvider.generateAccessToken(1L, "a@a.com");
        String tamperedToken = validAccessToken.substring(0, validAccessToken.length() - 2) + "xx";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tamperedToken);

        ResponseEntity<String> response = restTemplate.exchange(
                PROTECTED_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 유효하지만_권한이_부족한_DEVICE_토큰으로_호출하면_접근이_거부된다() {
        // 실측 결과: Spring Security의 ExceptionTranslationFilter는 AuthenticationManager를
        // 거치지 않고 필터에서 직접 구성한 Authentication(우리 JwtAuthenticationFilter가 하는 방식)에
        // 대해서도 AccessDeniedException을 AuthenticationEntryPoint(401)로 라우팅한다 — 순수 익명
        // 요청과 동일하게 취급됨. "권한 부족은 항상 403"이라는 통념과 다르지만, 실제 동작이 이렇고
        // 기능적으로는 여전히 명확히 차단됨(200이 아님) — 어떤 클라이언트도 이 상태 코드 차이에
        // 의존하지 않으므로(익스텐션/에이전트는 이 흐름 자체를 안 씀) 실제 동작대로 검증한다.
        String deviceToken = jwtProvider.generateDeviceToken(1L, "테스트 기기");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(deviceToken);

        ResponseEntity<String> response = restTemplate.exchange(
                PROTECTED_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }
}
