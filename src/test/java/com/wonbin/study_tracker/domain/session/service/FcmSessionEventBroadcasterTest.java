package com.wonbin.study_tracker.domain.session.service;

import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.global.push.PushMessageSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmSessionEventBroadcasterTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PushMessageSender pushMessageSender;

    @InjectMocks
    private FcmSessionEventBroadcaster broadcaster;

    @Test
    void push_토큰이_있는_모든_기기에_전송한다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device1 = buildDeviceWithPushToken(user, "token-1");
        Device device2 = buildDeviceWithPushToken(user, "token-2");

        when(deviceRepository.findByUserIdAndPushTokenIsNotNull(1L))
                .thenReturn(List.of(device1, device2));

        broadcaster.broadcast(1L, SessionEventType.STARTED, 100L);

        verify(pushMessageSender).send(eq("token-1"), any(Map.class));
        verify(pushMessageSender).send(eq("token-2"), any(Map.class));
    }

    @Test
    void 한_기기_전송이_실패해도_나머지_기기_전송은_계속된다() throws Exception {
        User user = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device1 = buildDeviceWithPushToken(user, "token-fail");
        Device device2 = buildDeviceWithPushToken(user, "token-ok");

        when(deviceRepository.findByUserIdAndPushTokenIsNotNull(1L))
                .thenReturn(List.of(device1, device2));
        doThrow(new RuntimeException("전송 실패")).when(pushMessageSender).send(eq("token-fail"), any(Map.class));

        broadcaster.broadcast(1L, SessionEventType.ENDED, 100L);

        verify(pushMessageSender).send(eq("token-ok"), any(Map.class));
    }

    private Device buildDeviceWithPushToken(User user, String pushToken) throws Exception {
        Device device = Device.builder()
                .user(user)
                .deviceName("테스트 기기")
                .deviceType("ANDROID")
                .deviceToken("device-token-" + pushToken)
                .build();
        device.updatePushToken(pushToken);
        return device;
    }
}
