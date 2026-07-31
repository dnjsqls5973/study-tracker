package com.wonbin.study_tracker.domain.user.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleIdentityResolverImpl implements GoogleIdentityResolver {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestClient googleRestClient;

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
        Map<String, Object> userInfo;
        try {
            userInfo = googleRestClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("유효하지 않은 Google 액세스 토큰입니다.", e);
        }

        if (userInfo == null || userInfo.get("sub") == null) {
            throw new IllegalArgumentException("유효하지 않은 Google 액세스 토큰입니다.");
        }

        return new GoogleIdentity(
                (String) userInfo.get("sub"),
                (String) userInfo.get("email"),
                (String) userInfo.get("name")
        );
    }
}
