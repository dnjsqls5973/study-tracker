package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleIdentityResolverImpl implements GoogleIdentityResolver {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestClient googleRestClient;

    @Value("${google.oauth.extension-client-id}")
    private final String extensionClientId;

    @Override
    public GoogleIdentity resolveFromIdToken(String idToken) {
        GoogleIdToken token;
        try {
            token = googleIdTokenVerifier.verify(idToken);
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 Google ID 토큰입니다.", e);
        }

        if (token == null) {
            throw new IllegalArgumentException("유효하지 않은 Google ID 토큰입니다.");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public GoogleIdentity resolveFromAccessToken(String accessToken) {
        Map<String, Object> tokenInfo;
        try {
            tokenInfo = googleRestClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?access_token={token}", accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("유효하지 않은 Google 액세스 토큰입니다.", e);
        }

        if (tokenInfo == null || tokenInfo.get("sub") == null) {
            throw new IllegalArgumentException("유효하지 않은 Google 액세스 토큰입니다.");
        }

        String audience = (String) tokenInfo.get("aud");
        if (audience == null) {
            audience = (String) tokenInfo.get("azp");
        }
        if (!extensionClientId.equals(audience)) {
            throw new IllegalArgumentException("허용되지 않은 클라이언트에서 발급된 토큰입니다.");
        }

        return new GoogleIdentity(
                (String) tokenInfo.get("sub"),
                (String) tokenInfo.get("email"),
                null
        );
    }
}
