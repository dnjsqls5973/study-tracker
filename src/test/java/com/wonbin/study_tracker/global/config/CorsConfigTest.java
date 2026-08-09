package com.wonbin.study_tracker.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void 운영_프론트엔드_도메인에서의_요청은_CORS를_허용한다() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "https://studytracker.cloud");

        ResponseEntity<String> response = restTemplate.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("https://studytracker.cloud");
    }
}
