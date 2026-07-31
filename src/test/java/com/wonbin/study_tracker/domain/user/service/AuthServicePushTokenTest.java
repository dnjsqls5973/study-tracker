package com.wonbin.study_tracker.domain.user.service;

import com.wonbin.study_tracker.api.auth.AuthRequest;
import com.wonbin.study_tracker.domain.device.entity.Device;
import com.wonbin.study_tracker.domain.device.repository.DeviceRepository;
import com.wonbin.study_tracker.domain.user.entity.User;
import com.wonbin.study_tracker.domain.user.repository.UserRepository;
import com.wonbin.study_tracker.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePushTokenTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GoogleIdentityResolver googleIdentityResolver;

    @InjectMocks
    private AuthService authService;

    @Test
    void 본인_소유_기기에는_push_토큰을_저장한다() throws Exception {
        User owner = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device = buildDevice(10L, owner);

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));

        AuthRequest.PushTokenUpdate request = new AuthRequest.PushTokenUpdate();
        setField(request, "deviceId", "10");
        setField(request, "pushToken", "fcm-token-123");

        authService.registerPushToken(1L, request);

        assertThat(device.getPushToken()).isEqualTo("fcm-token-123");
    }

    @Test
    void 다른_사용자의_기기에는_등록할_수_없다() throws Exception {
        User owner = User.builder().id(1L).email("a@a.com").name("A").googleId("g1").dayChangeHour(5).build();
        Device device = buildDevice(10L, owner);

        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));

        AuthRequest.PushTokenUpdate request = new AuthRequest.PushTokenUpdate();
        setField(request, "deviceId", "10");
        setField(request, "pushToken", "fcm-token-123");

        assertThatThrownBy(() -> authService.registerPushToken(999L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Device buildDevice(Long id, User owner) throws Exception {
        Device device = Device.builder()
                .user(owner)
                .deviceName("테스트 기기")
                .deviceType("ANDROID")
                .deviceToken("device-token")
                .build();
        setField(device, "id", id);
        return device;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
