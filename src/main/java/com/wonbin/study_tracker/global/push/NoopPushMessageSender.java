package com.wonbin.study_tracker.global.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
// firebase.credentials-path가 설정되지 않은 경우에만 등록된다.
// (havingValue="false" + matchIfMissing=true 는 "속성이 없을 때만 켜짐"을 표현하는 Spring Boot 관용구)
@ConditionalOnProperty(prefix = "firebase", name = "credentials-path", havingValue = "false", matchIfMissing = true)
public class NoopPushMessageSender implements PushMessageSender {

    @Override
    public void send(String token, Map<String, String> data) {
        log.info("[Push 비활성화] token={}, data={} (Firebase 설정 없음)", token, data);
    }
}
