package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.global.push.PushMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSessionEventBroadcaster implements SessionEventBroadcaster {

    private final DeviceRepository deviceRepository;
    private final PushMessageSender pushMessageSender;

    @Override
    public void broadcast(Long userId, SessionEventType eventType, Long sessionId) {
        List<Device> devices = deviceRepository.findByUserIdAndPushTokenIsNotNull(userId);

        Map<String, String> data = Map.of(
                "eventType", eventType.name(),
                "sessionId", String.valueOf(sessionId)
        );

        for (Device device : devices) {
            try {
                pushMessageSender.send(device.getPushToken(), data);
            } catch (RuntimeException e) {
                log.warn("push 전송 실패: deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }
    }
}
