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

    private static final String EXTENSION_CLIENT_ID = "test-extension-client-id";
    private static final String TOKEN_INFO_URI = "https://oauth2.googleapis.com/tokeninfo?access_token={token}";

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private RestClient googleRestClient;

    private GoogleIdentityResolverImpl resolver() {
        return new GoogleIdentityResolverImpl(googleIdTokenVerifier, googleRestClient, EXTENSION_CLIENT_ID);
    }

    @Test
    void ID_토큰이_유효하면_GoogleIdentity를_반환한다() throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-123");
        payload.setEmail("test@example.com");
        payload.set("name", "테스트유저");

        when(token.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify("valid-id-token")).thenReturn(token);

        GoogleIdentity identity = resolver().resolveFromIdToken("valid-id-token");

        assertThat(identity.googleId()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("test@example.com");
        assertThat(identity.name()).isEqualTo("테스트유저");
    }

    @Test
    void ID_토큰이_유효하지_않으면_예외를_던진다() throws Exception {
        when(googleIdTokenVerifier.verify("invalid-token")).thenReturn(null);

        assertThatThrownBy(() -> resolver().resolveFromIdToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void 액세스_토큰이_유효하면_GoogleIdentity를_반환한다() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        Map<String, Object> tokenInfo = Map.of(
                "sub", "google-sub-456",
                "email", "access@example.com",
                "aud", EXTENSION_CLIENT_ID
        );

        when(googleRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(TOKEN_INFO_URI, "valid-access-token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(tokenInfo);

        GoogleIdentity identity = resolver().resolveFromAccessToken("valid-access-token");

        assertThat(identity.googleId()).isEqualTo("google-sub-456");
        assertThat(identity.email()).isEqualTo("access@example.com");
        assertThat(identity.name()).isNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void 액세스_토큰이_유효하지_않으면_예외를_던진다() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(googleRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(TOKEN_INFO_URI, "invalid-access-token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> resolver().resolveFromAccessToken("invalid-access-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void 다른_클라이언트에서_발급된_액세스_토큰이면_예외를_던진다() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        Map<String, Object> tokenInfo = Map.of(
                "sub", "google-sub-789",
                "email", "victim@example.com",
                "aud", "other-app-client-id.apps.googleusercontent.com"
        );

        when(googleRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(TOKEN_INFO_URI, "foreign-access-token")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(tokenInfo);

        assertThatThrownBy(() -> resolver().resolveFromAccessToken("foreign-access-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않은 클라이언트");
    }
}
