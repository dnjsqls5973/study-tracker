package com.wonbin.study_tracker.global.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseMessaging.class)
public class FirebaseCloudMessagingSender implements PushMessageSender {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public void send(String token, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build();
        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: token={}, error={}", token, e.getMessage());
        }
    }
}
