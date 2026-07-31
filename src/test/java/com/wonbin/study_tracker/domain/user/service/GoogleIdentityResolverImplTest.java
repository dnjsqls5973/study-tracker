package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void 액세스_토큰이_유효하면_GoogleIdentity를_반환한다() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        Map<String, Object> userInfo = Map.of(
                "sub", "google-sub-456",
                "email", "access@example.com",
                "name", "액세스유저"
        );

        when(googleRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("https://www.googleapis.com/oauth2/v3/userinfo")).thenReturn(headersSpec);
        when(headersSpec.header("Authorization", "Bearer valid-access-token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(userInfo);

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        GoogleIdentity identity = resolver.resolveFromAccessToken("valid-access-token");

        assertThat(identity.googleId()).isEqualTo("google-sub-456");
        assertThat(identity.email()).isEqualTo("access@example.com");
        assertThat(identity.name()).isEqualTo("액세스유저");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void 액세스_토큰이_유효하지_않으면_예외를_던진다() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(googleRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("https://www.googleapis.com/oauth2/v3/userinfo")).thenReturn(headersSpec);
        when(headersSpec.header("Authorization", "Bearer invalid-access-token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        GoogleIdentityResolverImpl resolver =
                new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient);

        assertThatThrownBy(() -> resolver.resolveFromAccessToken("invalid-access-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
