package com.wonbin.study_tracker.api.auth;

import lombok.Builder;
import lombok.Getter;

public class AuthResponse {

    @Getter
    @Builder
    public static class Token {
        private String accessToken;
        private String refreshToken;
        private Long userId;
        private String name;
    }

    @Getter
    @Builder
    public static class DeviceToken {
        private String deviceToken;
        private Long deviceId;
    }
}
