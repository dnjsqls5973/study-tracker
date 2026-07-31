package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdentityResolverImplTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private RestClient googleRestClient;

    @Test
    void ID_토큰이_유효하면_GoogleIdentity를_반환한다() throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("test@example.com");
        payload.set("name", "테스트유저");

        when(token.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify("valid-id-token")).thenReturn(token);

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        GoogleIdentity identity = resolver.resolveFromIdToken("valid-id-token");

        assertThat(identity.googleId()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("test@example.com");
        assertThat(identity.name()).isEqualTo("테스트유저");
    }

    @Test
    void ID_토큰이_유효하지_않으면_예외를_던진다() throws Exception {
        when(googleIdTokenVerifier.verify("invalid-token")).thenReturn(null);

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        assertThatThrownBy(() -> resolver.resolveFromIdToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
