package com.wonbin.study_tracker.global.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class GoogleAuthConfig {

    // 크롬 확장 프로그램의 client-id(google.oauth.extension-client-id)는 의도적으로 이 audience 목록에
    // 포함하지 않는다. 확장 프로그램은 ID 토큰이 아닌 access-token/tokeninfo 방식으로 별도 검증되며,
    // 그 로직은 GoogleIdentityResolverImpl에 있다. 새로운 클라이언트를 추가할 때는 ID 토큰 기반 로그인인지
    // access-token 기반 로그인인지에 따라 이 빈 또는 GoogleIdentityResolverImpl 중 알맞은 곳을 수정해야 한다.
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.oauth.client-id}") String clientId,
            @Value("${google.oauth.pc-agent-client-id:}") String pcAgentClientId) {
        List<String> audience = new ArrayList<>();
        audience.add(clientId);
        if (StringUtils.hasText(pcAgentClientId)) {
            audience.add(pcAgentClientId);
        }
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audience)
                .build();
    }

    @Bean
    public RestClient googleRestClient() {
        return RestClient.create();
    }
}
