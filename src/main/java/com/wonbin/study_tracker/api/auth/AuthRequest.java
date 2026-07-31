package com.wonbin.study_tracker.api.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public class AuthRequest {

    @Getter
    public static class GoogleIdTokenLogin {
        @NotBlank
        private String idToken;
    }

    @Getter
    public static class GoogleAccessTokenLogin {
        @NotBlank
        private String accessToken;
    }

    @Getter
    public static class DeviceRegister {
        @NotBlank
        private String deviceName;

        @NotBlank
        private String deviceType;
    }

    @Getter
    public static class PushTokenUpdate {
        @NotBlank
        private String deviceId;

        @NotBlank
        private String pushToken;
    }
}
