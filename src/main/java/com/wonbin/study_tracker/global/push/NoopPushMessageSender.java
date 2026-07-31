package com.wonbin.study_tracker.global.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnMissingBean(PushMessageSender.class)
public class NoopPushMessageSender implements PushMessageSender {

    @Override
    public void send(String token, Map<String, String> data) {
        log.info("[Push 비활성화] token={}, data={} (Firebase 설정 없음)", token, data);
    }
}
