package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GoogleIdentityResolverImpl의 extensionClientId가 실제 Spring 컨테이너에서
 * 생성자 주입을 통해 정상적으로 주입되는지 검증한다.
 * <p>
 * 이 테스트는 {@link GoogleIdentityResolverImplTest}와 달리 {@code new GoogleIdentityResolverImpl(...)}로
 * 직접 생성하지 않고, ApplicationContextRunner로 실제 Spring 컨텍스트를 구성하여
 * {@code @Value}가 생성자 파라미터에 올바르게 적용되어 있는지를 확인한다.
 * <p>
 * 수정 전 코드({@code @RequiredArgsConstructor} + 필드의 {@code @Value})에서는 Lombok이
 * 생성한 생성자 파라미터에 {@code @Value} 애노테이션이 복사되지 않아, Spring이 String 타입의
 * extensionClientId 파라미터를 빈으로 오토와이어링하려다 실패하며
 * {@code UnsatisfiedDependencyException}이 발생했다. 이 테스트는 그 상황을 재현하도록 설계되었으며,
 * 수정된 코드(생성자 파라미터에 직접 {@code @Value})에서는 컨텍스트가 정상적으로 기동되어야 한다.
 */
class GoogleIdentityResolverImplWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDependenciesConfig.class)
            .withBean(GoogleIdentityResolverImpl.class);

    @Test
    void extensionClientId_프로퍼티가_생성자에_실제로_주입된다() {
        contextRunner
                .withPropertyValues("google.oauth.extension-client-id=test-client-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleIdentityResolverImpl.class);
                    assertThat(context.getBean(GoogleIdentityResolverImpl.class)).isNotNull();
                });
    }

    @Configuration
    static class TestDependenciesConfig {

        @Bean
        GoogleIdTokenVerifier googleIdTokenVerifier() {
            return mock(GoogleIdTokenVerifier.class);
        }

        @Bean
        RestClient googleRestClient() {
            return mock(RestClient.class);
        }
    }
}
