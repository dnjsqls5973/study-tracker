package com.wonbin.study_tracker.global.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthConfigTest {

    private final GoogleAuthConfig config = new GoogleAuthConfig();

    @Test
    void 웹_클라이언트와_PC_에이전트_클라이언트_ID를_모두_audience로_허용한다() {
        GoogleIdTokenVerifier verifier = config.googleIdTokenVerifier("web-client-id", "pc-agent-client-id");

        assertThat(verifier.getAudience()).containsExactlyInAnyOrder("web-client-id", "pc-agent-client-id");
    }
}
