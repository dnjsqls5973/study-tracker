package com.wonbin.study_tracker.global.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoogleAuthConfig의 googleIdTokenVerifier 빈이 실제 Spring 컨테이너에서
 * 프로퍼티 플레이스홀더 해석까지 포함하여 정상적으로 기동되는지 검증한다.
 * <p>
 * {@link GoogleAuthConfigTest}는 빈 팩토리 메서드를 리터럴 문자열 인자로 직접 호출하므로
 * {@code @Value} 프로퍼티 해석 문제를 검증할 수 없다. 이 테스트는
 * {@code GoogleIdentityResolverImplWiringTest}와 동일한 패턴으로 ApplicationContextRunner를 사용해,
 * google.oauth.pc-agent-client-id 프로퍼티가 아직 설정되지 않은 환경(데스크톱 앱 OAuth 클라이언트를
 * 아직 생성하지 않은 경우)에서도 컨텍스트가 정상적으로 기동되는지 확인한다.
 * <p>
 * 수정 전 코드({@code @Value("${google.oauth.pc-agent-client-id}")}, 기본값 없음)에서는
 * 해당 프로퍼티가 없으면 플레이스홀더 해석에 실패하여 전체 애플리케이션 컨텍스트가 기동하지 못했다.
 * 이는 데스크톱 에이전트뿐 아니라 웹/크롬 확장 프로그램 로그인까지 포함한 전체 인증 시스템을 마비시키는
 * 문제였다. 수정된 코드(빈 기본값 {@code :} + blank 필터링)에서는 프로퍼티가 없어도 컨텍스트가
 * 정상적으로 기동되어야 한다.
 */
class GoogleAuthConfigWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GoogleAuthConfig.class);

    @Test
    void 웹_클라이언트와_PC_에이전트_클라이언트_ID가_모두_설정되면_컨텍스트가_정상_기동된다() {
        contextRunner
                .withPropertyValues(
                        "google.oauth.client-id=web-client-id",
                        "google.oauth.pc-agent-client-id=pc-agent-client-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleIdTokenVerifier.class);
                });
    }

    @Test
    void PC_에이전트_클라이언트_ID_프로퍼티가_아예_없어도_컨텍스트가_정상_기동된다() {
        contextRunner
                .withPropertyValues("google.oauth.client-id=web-client-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleIdTokenVerifier.class);
                });
    }
}
